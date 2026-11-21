"""Check ELF PT_LOAD alignment of 64-bit native libraries inside an AAB or APK."""
import argparse
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('archive')
args = parser.parse_args()
checked = 0
failures = []
with zipfile.ZipFile(args.archive) as archive:
    for name in archive.namelist():
        if not name.endswith('.so'):
            continue
        data = archive.read(name)
        if data[:4] != b'\x7fELF':
            raise ValueError(f'Not an ELF library: {name}')
        if data[4] != 2:  # Android 16KB requirements concern 64-bit device ABIs.
            continue
        endian = '<' if data[5] == 1 else '>'
        phoff = struct.unpack_from(endian + 'Q', data, 32)[0]
        entry_size, count = struct.unpack_from(endian + 'HH', data, 54)
        segments = [struct.unpack_from(endian + 'IIQQQQQQ', data, phoff + i * entry_size) for i in range(count)]
        alignment = [segment[7] for segment in segments if segment[0] == 1]
        checked += 1
        valid = bool(alignment) and all(value >= 16384 for value in alignment)
        print(f'{"PASS" if valid else "FAIL"}: {name}: PT_LOAD alignment={alignment}')
        if not valid:
            failures.append(name)
if failures:
    raise SystemExit('Incompatible 64-bit native libraries found.')
print(f'Checked {checked} 64-bit native libraries. This does not replace runtime testing on a 16KB device.')

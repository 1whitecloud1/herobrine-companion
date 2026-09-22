"""Run with Python 3.9+. Default is a dry run; --apply restores the pre-install files."""
import argparse
import hashlib
import json
from pathlib import Path

BASE=Path(__file__).resolve().parent
ALLOWED=[Path('E:/MCStudioDownload/work/m13525918851@163.com/Cpp/AddOn/'+name).resolve()
         for name in ('adfe8b2fa8444c3aaffbeca53d5ef7d8','3055269d404f439eba5592a9d4b52113')]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description='Restore only this animation integration, preserving later edits.')
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    journal=json.loads((BASE/'restore_manifest.json').read_text(encoding='utf-8'))
    operations=[]
    for entry in journal['files']:
        target=(Path(entry['project'])/entry['relative']).resolve()
        assert any(target.is_relative_to(root) for root in ALLOWED),'Unexpected target: '+str(target)
        if not target.exists():
            if entry['created']:
                continue
            raise RuntimeError('An original file is now missing: '+str(target))
        current=sha(target)
        if current==entry.get('before_sha256'):
            continue
        if current!=entry['installed_sha256']:
            raise RuntimeError('Preserving a file edited after installation: '+str(target))
        backup=None
        if not entry['created']:
            backup=(BASE/entry['backup_relative']).resolve()
            assert backup.is_relative_to(BASE)
            assert sha(backup)==entry['before_sha256'],'Backup checksum mismatch'
        operations.append((target,backup))
    print(('RESTORE' if args.apply else 'DRY RUN'),len(operations),'files')
    if not args.apply:
        return
    # Each resolved target above is checked. No recursive delete or directory move.
    for target,backup in operations:
        if backup is None:
            target.unlink()
        else:
            target.write_bytes(backup.read_bytes())
    print('Restored the state before this integration.')


if __name__=='__main__':main()

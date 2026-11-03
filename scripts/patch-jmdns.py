"""Build the single-class RFC6762 legacy-unicast fix without changing pinned downloads."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile

parser=argparse.ArgumentParser();parser.add_argument('--javac',default='javac');args=parser.parse_args()
root=Path(__file__).resolve().parent.parent;deps=root/'.local/java-deps'
for item in json.loads((root/'scripts/java-dependencies.json').read_text(encoding='utf-8')):
    assert hashlib.sha256((deps/item['name']).read_bytes()).hexdigest()==item['sha256'],'Unverified dependency: '+item['name']
original=deps/'jmdns-3.6.3.jar';classes=root/'.local/jmdns-patch-classes';classes.mkdir(exist_ok=True)
subprocess.run([args.javac,'-encoding','UTF-8','-source','8','-target','8','-classpath',os.pathsep.join([str(original),str(deps/'slf4j-api-2.0.17.jar')]),'-d',str(classes),str(root/'vendor/jmdns/SocketListener.java')],check=True)
entry='javax/jmdns/impl/SocketListener.class';replacement=(classes/entry).read_bytes()
destination=root/'.local/java-deps-patched/jmdns-3.6.3.jar';destination.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(original) as src,zipfile.ZipFile(destination,'w') as dst:
    assert entry in src.namelist()
    assert not any(n.upper().endswith(('.SF','.RSA','.DSA')) for n in src.namelist()),'Signed dependency cannot be patched'
    for info in src.infolist():dst.writestr(info,replacement if info.filename==entry else src.read(info.filename))
with zipfile.ZipFile(original) as src,zipfile.ZipFile(destination) as dst:
    assert src.namelist()==dst.namelist()
    changed=[name for name in src.namelist() if src.read(name)!=dst.read(name)]
    assert changed==[entry],changed
print('JmDNS patched: exactly one class changed; all other classes, license and metadata retained')

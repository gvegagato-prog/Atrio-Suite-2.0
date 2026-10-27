#!/usr/bin/env python3
"""Sube un APK a «Mi carpeta/versiones» de tu kDrive (para que la app se actualice sola desde ahí).

Uso:  python3 herramientas/subir-version.py Atrio-6.67.apk
La autenticación la pone, o bien el entorno de la sesión (un secreto de red para api.infomaniak.com que añade
«Authorization: Bearer …» solo), o bien la variable KDRIVE_TOKEN si la tienes. El kDrive se elige con KDRIVE_ID
(por defecto, el único de la cuenta). El nombre en kDrive es el del archivo: debe ser «Atrio-<versión>.apk».
"""
import hashlib, json, os, re, sys, urllib.parse, urllib.request, urllib.error

API = "https://api.infomaniak.com"
CARPETA = "versiones"


def llamar(metodo, url, cuerpo=None, tipo="application/json"):
    cab = {"Content-Type": tipo}
    if os.environ.get("KDRIVE_TOKEN"):
        cab["Authorization"] = "Bearer " + os.environ["KDRIVE_TOKEN"]
    pet = urllib.request.Request(url, data=cuerpo, method=metodo, headers=cab)
    try:
        with urllib.request.urlopen(pet, timeout=300) as r:
            return json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        raise SystemExit(f"kDrive {e.code} en {metodo} {url.split('?')[0]}: {e.read().decode()[:300]}")


def kdrive():
    if os.environ.get("KDRIVE_ID"):
        return os.environ["KDRIVE_ID"]
    cuentas = llamar("GET", f"{API}/1/account")["data"]
    drives = []
    for c in cuentas:
        drives += llamar("GET", f"{API}/2/drive?account_id={c['id']}")["data"]
    if len(drives) != 1:
        raise SystemExit("Hay %d kDrive; elige uno con KDRIVE_ID (%s)" % (len(drives), ", ".join(f"{d['id']}={d['name']}" for d in drives)))
    return str(drives[0]["id"])


def carpeta_privada(d):
    r = llamar("GET", f"{API}/3/drive/{d}/files/1/files")["data"]
    for x in r:
        if x.get("type") == "dir" and x.get("visibility") == "is_private_space":
            return x["id"]
    raise SystemExit("No encuentro «Mi carpeta» en el kDrive")


def subir(ruta):
    nombre = os.path.basename(ruta)
    if not re.fullmatch(r"Atrio-\d+(\.\d+)*\.apk", nombre):
        raise SystemExit("El archivo debe llamarse Atrio-<versión>.apk (p. ej. Atrio-6.67.apk)")
    datos = open(ruta, "rb").read()
    d = kdrive()
    home = carpeta_privada(d)
    q = lambda v: urllib.parse.quote(str(v), safe="")
    tam = len(datos)
    trozo = max(1 << 20, (tam + 199) // 200)
    total = (tam + trozo - 1) // trozo
    ini = llamar("POST", f"{API}/3/drive/{d}/upload/session/start", json.dumps({
        "conflict": "version", "directory_id": home, "directory_path": CARPETA, "file_name": nombre,
        "total_chunks": total, "total_size": tam}).encode())["data"]
    host = (os.environ.get("KDRIVE_HOST") or ini.get("upload_url") or API).rstrip("/")
    print(f"Subiendo {nombre} ({tam / 1e6:.1f} MB) en {total} trozos a «Mi carpeta/{CARPETA}» (kDrive {d}, servidor {urllib.parse.urlparse(host).netloc})")
    for n in range(1, total + 1):
        parte = datos[(n - 1) * trozo:n * trozo]
        llamar("POST", f"{host}/3/drive/{d}/upload/session/{ini['token']}/chunk?chunk_number={n}&chunk_size={len(parte)}", parte, "application/octet-stream")
    fin = llamar("POST", f"{API}/3/drive/{d}/upload/session/{ini['token']}/finish")["data"]["file"]
    print(f"Hecho: {fin.get('name')} (id {fin.get('id')}, {fin.get('size')} bytes)")
    return fin


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    subir(sys.argv[1])

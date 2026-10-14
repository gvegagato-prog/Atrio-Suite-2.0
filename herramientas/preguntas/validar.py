#!/usr/bin/env python3
"""Valida un banco de preguntas de Descubrir. Uso: python3 validar.py pantalla|libros fichero.json [otro.json ...]"""
import json, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
SCREEN_G = {"action","adventure","animation","comedy","crime","documentary","drama","family","fantasy","history","horror",
            "music","mystery","romance","scifi","thriller","war","western","kids","reality","politics"}
ERA_S = {"classic","80s90s","2000s","recent"}
ERA_B = {"classic","20c","modern","recent"}
LEN = {"short","medium","long"}
TYPE = {"movie","tv"}
LANG_S = {"en","es","ja","ko","fr","it","de","hi","foreign"}
LANG_B = {"spa","eng","any"}
POP = {"hidden","mainstream","cult"}
FIC = {"fiction","nonfiction"}
SUBJ = set(l.strip() for l in open(os.path.join(HERE, "temas-libros.txt")) if l.strip())
AXES_S = {"mood","genre","era","length","format","language","popularity","theme","setting","company","pace","quality","style"}
AXES_B = {"mood","genre","era","length","language","popularity","theme","setting","pace","style","fiction","purpose"}

def err(m): print("ERROR:", m); global bad; bad += 1
bad = 0
domain = sys.argv[1]
seen_ids, seen_q = set(), set()
total = 0
for f in sys.argv[2:]:
    data = json.load(open(f, encoding="utf-8"))
    if not isinstance(data, list): err(f"{f}: no es una lista"); continue
    for q in data:
        total += 1
        qid = q.get("id"); txt = q.get("q", "")
        if not qid or qid in seen_ids: err(f"id repetido o vacío: {qid}")
        seen_ids.add(qid)
        if not txt.strip() or len(txt) > 110: err(f"{qid}: pregunta vacía o muy larga")
        k = txt.strip().lower()
        if k in seen_q: err(f"{qid}: pregunta repetida: {txt}")
        seen_q.add(k)
        if q.get("axis") not in (AXES_S if domain == "pantalla" else AXES_B): err(f"{qid}: eje no válido {q.get('axis')}")
        if domain == "pantalla" and q.get("for", "any") not in ("any","movie","tv"): err(f"{qid}: for no válido")
        opts = q.get("a", [])
        if not (2 <= len(opts) <= 6): err(f"{qid}: entre 2 y 6 respuestas")
        for o in opts:
            if not o.get("t") or len(o["t"]) > 60: err(f"{qid}: texto de respuesta vacío o largo: {o.get('t')}")
            e = o.get("e", {})
            if not isinstance(e, dict): err(f"{qid}: e no es objeto"); continue
            for key, v in e.items():
                if domain == "pantalla":
                    if key == "g":
                        for g, w in v.items():
                            if g not in SCREEN_G or not isinstance(w, (int, float)) or not -3 <= w <= 3: err(f"{qid}: g {g}={w}")
                    elif key == "kw":
                        if not all(isinstance(x, str) and 2 < len(x) < 40 for x in v): err(f"{qid}: kw")
                    elif key == "era":
                        if v not in ERA_S: err(f"{qid}: era {v}")
                    elif key == "len":
                        if v not in LEN: err(f"{qid}: len {v}")
                    elif key == "type":
                        if v not in TYPE: err(f"{qid}: type {v}")
                    elif key == "lang":
                        if v not in LANG_S: err(f"{qid}: lang {v}")
                    elif key == "pop":
                        if v not in POP: err(f"{qid}: pop {v}")
                    elif key == "rate":
                        if not isinstance(v, (int, float)) or not 5 <= v <= 8.5: err(f"{qid}: rate {v}")
                    else: err(f"{qid}: clave desconocida {key}")
                else:
                    if key == "s":
                        for s, w in v.items():
                            if s not in SUBJ or not isinstance(w, (int, float)) or not -3 <= w <= 3: err(f"{qid}: s {s}={w}")
                    elif key == "era":
                        if v not in ERA_B: err(f"{qid}: era {v}")
                    elif key == "len":
                        if v not in LEN: err(f"{qid}: len {v}")
                    elif key == "lang":
                        if v not in LANG_B: err(f"{qid}: lang {v}")
                    elif key == "pop":
                        if v not in POP: err(f"{qid}: pop {v}")
                    elif key == "fic":
                        if v not in FIC: err(f"{qid}: fic {v}")
                    else: err(f"{qid}: clave desconocida {key}")
print(f"{total} preguntas, {bad} errores")
sys.exit(1 if bad else 0)

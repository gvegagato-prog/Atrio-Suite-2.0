#!/usr/bin/env python3
"""Valida un banco de preguntas de juegos (JSON). Uso: validar_juegos.py fichero.json [más.json]"""
import json, sys

GENRES = {"action", "adventure", "role-playing-games-rpg", "shooter", "indie", "platformer", "puzzle", "racing", "sports",
          "strategy", "simulation", "fighting", "family", "arcade", "casual", "massively-multiplayer", "board-games", "card", "educational"}
TAGS = {"singleplayer", "multiplayer", "co-op", "online-co-op", "local-co-op", "split-screen", "pvp", "atmospheric",
        "great-soundtrack", "story-rich", "open-world", "first-person", "third-person", "sci-fi", "horror", "fantasy", "funny",
        "difficult", "exploration", "sandbox", "survival", "crafting", "roguelike", "souls-like", "metroidvania", "pixel-graphics",
        "2d", "stealth", "female-protagonist", "choices-matter", "multiple-endings", "turn-based", "relaxing", "cute", "anime",
        "cyberpunk", "post-apocalyptic", "zombies", "space", "war", "medieval", "mystery", "detective", "psychological-horror",
        "survival-horror", "dark-fantasy", "hack-and-slash", "jrpg", "city-builder", "management", "visual-novel", "point-click",
        "walking-simulator", "fps", "tactical", "building", "competitive", "free-to-play", "short", "replay-value", "emotional",
        "nature", "historical", "dark", "violent", "gore", "magic", "dragons", "pirates", "ninja", "robots", "aliens", "time-travel",
        "cartoon", "colorful", "comedy", "realistic", "physics", "music", "dating-sim", "farming", "fishing", "cooking"}
VOTES = {"era": {"retro", "classic", "modern", "recent"}, "len": {"short", "medium", "long"},
         "plat": {"pc", "playstation", "xbox", "nintendo", "mobile"}, "pop": {"hidden", "mainstream", "cult"},
         "mode": {"solo", "coop", "online"}}
AXES = {"mood", "genre", "theme", "era", "platform", "mode", "length", "difficulty", "style", "setting", "pace", "story",
        "popularity", "quality", "company"}

def check(path, seen):
    errs = []
    data = json.load(open(path, encoding="utf-8"))
    if not isinstance(data, list): return [f"{path}: no es una lista"]
    for q in data:
        qid = q.get("id", "?")
        if qid in seen: errs.append(f"{qid}: id repetido")
        seen.add(qid)
        if not isinstance(q.get("q"), str) or len(q["q"]) < 8: errs.append(f"{qid}: pregunta vacía")
        if q.get("axis") not in AXES: errs.append(f"{qid}: eje {q.get('axis')}")
        a = q.get("a", [])
        if not (3 <= len(a) <= 5): errs.append(f"{qid}: {len(a)} respuestas")
        for r in a:
            if not isinstance(r.get("t"), str) or not r["t"].strip(): errs.append(f"{qid}: respuesta sin texto")
            e = r.get("e", {})
            for k, v in e.items():
                if k == "g":
                    for g, w in v.items():
                        if g not in GENRES: errs.append(f"{qid}: género {g}")
                        if not isinstance(w, (int, float)) or abs(w) > 3: errs.append(f"{qid}: peso {w}")
                elif k == "tg":
                    for t, w in v.items():
                        if t not in TAGS: errs.append(f"{qid}: etiqueta {t}")
                        if not isinstance(w, (int, float)) or abs(w) > 3: errs.append(f"{qid}: peso {w}")
                elif k in VOTES:
                    if v not in VOTES[k]: errs.append(f"{qid}: {k}={v}")
                elif k == "rate":
                    if not isinstance(v, (int, float)) or not 60 <= v <= 95: errs.append(f"{qid}: rate {v}")
                else: errs.append(f"{qid}: clave {k}")
        texts = [r.get("t", "").strip().lower() for r in a]
        if len(set(texts)) != len(texts): errs.append(f"{qid}: respuestas repetidas")
    qs = [q.get("q", "").strip().lower() for q in data]
    dup = {x for x in qs if qs.count(x) > 1}
    for d in dup: errs.append(f"pregunta repetida: {d}")
    return errs

seen = set(); total = 0; allerr = []
for p in sys.argv[1:]:
    allerr += check(p, seen); total += len(json.load(open(p, encoding="utf-8")))
print(f"{total} preguntas, {len(allerr)} errores")
for e in allerr[:60]: print(" -", e)
sys.exit(1 if allerr else 0)

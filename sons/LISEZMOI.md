# Sons du jeu

Dépose ici les fichiers audio (`.ogg` conseillé, `.mp3` ou `.wav`), tels quels : ils sont intégrés
automatiquement dans l'appli à la compilation (les noms sont mis en minuscules et `-` ou
espaces deviennent `_`, ex. `Oh-yeah.mp3` → `oh_yeah`).

| Fichier | Utilisation |
|---|---|
| `boucle1` … `boucle4` | musique de fond : chaque boucle est répétée 3-4 fois, puis une autre au hasard |
| `bonus` | musique pendant les niveaux bonus |
| `gameover2` | quand on perd |
| `chute` | quand le personnage tombe |
| `limite`, `limite2`, `limite3` | atterrissage tout au bord (un au hasard) |
| `perfect`, `oh_yeah`, `ifeelgood` | atterrissage parfait (un au hasard) |
| `great` | saut réussi, environ 1 fois sur 5 |
| `congratulations` | nouvel élément de personnage débloqué |
| `ange` | vie bonus utilisée (« Halleluja ») |

Autres sons facultatifs reconnus : `piece` (pièce ramassée), `saut`, `atterrissage`, `effondrement`, `bonus_gagne`,
`bonus_rate`, `tape`, `etoile`, `roue`, `clic`.

Les fichiers sont en OGG Vorbis (≈ 96 kbit/s pour les musiques) pour garder l'APK léger.
Pour convertir un MP3 : `ffmpeg -i son.mp3 -c:a libvorbis -q:a 3 son.ogg` (`-q:a 2` pour une musique).

# Langues du jeu

Chaque langue est un fichier JSON : `fr.json` (français, la référence), `en.json`, `es.json`,
`de.json`, `pt.json`, `it.json`. Ils sont utilisés à la fois par l'appli Android et par la
version navigateur.

## Corriger une traduction

Ouvre le fichier de la langue et modifie le texte **à droite** des deux-points. Ne touche pas
à la clé de gauche.

```json
"over.replay": "Play again",
```

- `{n}`, `{name}`, `{item}`… sont remplacés par le jeu (un nombre, un pseudo, un objet…) :
  il faut les garder tels quels, mais on peut les déplacer dans la phrase.
- `\n` = retour à la ligne.
- Les guillemets `"` à l'intérieur d'un texte s'écrivent `\"` (ou utiliser “ ” « » „ “).

## Ajouter une langue

1. Copier `en.json` (ou `fr.json`) sous le nom du code de la langue, par ex. `nl.json`.
2. Changer `"_nom"` (nom de la langue dans cette langue, ex. `"Nederlands"`) et traduire tous
   les textes.
3. Ajouter le code dans la liste `languages` de `app/src/main/java/com/sautique/game/I18n.kt`
   et dans `LANGUAGES` de `web/js/i18n.js`.

Un test automatique vérifie que chaque fichier a exactement les mêmes clés et les mêmes `{…}`
que `fr.json`. Si une clé manque malgré tout, le jeu affiche le texte français.

## Choix de la langue

Par défaut, le jeu prend la langue du téléphone (ou du navigateur), et l'anglais si elle n'est
pas disponible. On peut la changer dans les réglages (bouton 🔊 Son → 🌐 Langue).

# Sautique

Petit jeu Android en vue isométrique (effet 3D) : maintiens le doigt appuyé sur l'écran pour charger le saut,
relâche pour sauter. Plus l'appui est long, plus le personnage va loin.

- La plateforme de départ est fixe, une nouvelle plateforme apparaît plus loin.
- 1 saut réussi = 1 point (compteur en gros en haut de l'écran).
- Saut trop court sur ta propre plateforme : pas de point, tu peux réessayer.
- Tomber dans le vide = partie terminée. Le record est sauvegardé.
- Les plateformes (cubes ou cylindres) rétrécissent au fil des points et apparaissent
  tantôt vers la droite, tantôt vers la gauche.

## Récupérer l'APK

Chaque push lance le workflow GitHub Actions **Build APK**. Ouvre l'onglet
*Actions* du dépôt, choisis la dernière exécution et télécharge l'artefact
`sautique-apk` (un zip contenant `app-debug.apk`). Copie l'APK sur ton téléphone
et installe-le (autorise « sources inconnues » si demandé).

## Compiler en local

Avec le SDK Android installé (ou Android Studio) :

```bash
./gradlew assembleDebug
```

L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

Le code du jeu est dans `app/src/main/java/com/sautique/game/GameView.kt`.

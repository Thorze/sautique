# Sautique

Petit jeu Android en vue isométrique (effet 3D) : maintiens le doigt appuyé sur l'écran pour charger le saut,
relâche pour sauter. Plus l'appui est long, plus le personnage va loin.

- La plateforme de départ est fixe, une nouvelle plateforme apparaît plus loin.
- 1 saut réussi = 1 point (compteur en gros en haut de l'écran).
- Saut trop court sur ta propre plateforme : pas de point, tu peux réessayer.
- Tomber dans le vide = partie terminée. Le record est sauvegardé.
- **Bonus** tous les 10 sauts réussis : un curseur va et vient sur une barre rouge avec une
  zone verte au milieu. Touche l'écran quand il est dans le vert : +5 points. Raté : pas de
  bonus, mais la partie continue.
  Le curseur accélère et la zone verte rétrécit à chaque bonus.
- À partir de 25 points apparaissent des **plateformes fragiles** (fissurées) : dès que tu
  atterris dessus elles tremblent puis s'effondrent, il faut ressauter vite.
- **Personnage** : choisis ta forme (cube, boule, cylindre, tour), ta couleur et tes
  accessoires (lunettes, chapeau, chaussures) via le bouton 🎨. Les points de toutes tes
  parties s'additionnent et débloquent de nouveaux éléments.
- Chaque chute joue une animation au hasard : vrille, panique, parachute qui craque,
  ou pause « dessin animé » avant de tomber.
- Atterrir tout au bord fait **trembler** le personnage, avec un « ! » de frayeur.
- Les plateformes (cubes ou cylindres) rétrécissent au fil des points et apparaissent
  tantôt vers la droite, tantôt vers la gauche.

## Pseudo et classements

- Au premier lancement, le jeu demande un **pseudo** (modifiable ensuite via le bouton ✎).
- **Top perso** : tes 10 meilleures parties, gardées sur le téléphone.
- **Top mondial** : le meilleur score de chaque joueur, partagé entre tous les téléphones,
  avec ta place au classement.

### Activer le top mondial (une seule fois)

Les scores sont stockés sur ton VPS par un petit serveur fourni dans `server/`.

1. Installe le serveur sur le VPS : voir [`server/README.md`](server/README.md)
   (Docker ou Python, quelques commandes).
2. Vérifie depuis ton téléphone que `http://IP_DU_VPS:8080/health` répond `"ok"`.
3. Indique l'adresse du serveur à l'appli, au choix :
   - dans `gradle.properties` : `SCORES_URL=http://IP_DU_VPS:8080`, puis commit/push ;
   - ou en secret GitHub : **Settings** > **Secrets and variables** > **Actions** >
     **New repository secret**, nom `SCORES_URL`.
   Si tu as mis une clé `SAUTIQUE_KEY` sur le serveur, ajoute le secret `SCORES_KEY`
   avec la même valeur.
4. Récupère le nouvel APK généré par le workflow **Build APK** (relance-le via
   *Run workflow* si tu n'as utilisé que les secrets).

Tant que ce n'est pas configuré, le jeu marche normalement et le top mondial
affiche « Classement mondial non configuré ».

## Mises à jour automatiques

Au lancement, le jeu regarde la dernière release GitHub de `Thorze/sautique`. Si elle est
plus récente, il propose de la télécharger et de l'installer (Android demande une fois
l'autorisation « installer des applis inconnues »). Toucher le numéro de version en bas à
gauche de l'écran d'accueil relance la vérification.

Prérequis (une seule fois) :

1. Le dépôt doit être **public** : Settings > General > Danger Zone > Change visibility.
2. Trois secrets GitHub (Settings > Secrets and variables > Actions) pour la clé de
   signature fixe : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`. Toutes les versions
   doivent être signées avec la même clé, sinon Android refuse la mise à jour.
   **Garde une copie de cette clé** : si elle est perdue, il faudra désinstaller l'appli
   sur tous les téléphones.

### Publier une nouvelle version

- Onglet **Actions** > **Release** > **Run workflow**, saisis la version (ex. `1.5`) ;
- ou en ligne de commande : `git tag v1.5 && git push origin v1.5`.

Le workflow compile l'APK signé et crée la release `v1.5` avec le fichier
`Sautique-1.5.apk`. Les numéros doivent toujours augmenter (1.4 → 1.5 → 1.5.1 → 2.0).

## Récupérer l'APK

Installe l'APK de la dernière [release](https://github.com/Thorze/sautique/releases/latest).
Ensuite, les mises à jour se font depuis le jeu.

Pour tester une version en cours de développement : chaque push lance aussi le workflow
**Build APK**, qui produit l'artefact `sautique-apk` (`app-debug.apk`).

## Compiler en local

Avec le SDK Android installé (ou Android Studio) :

```bash
./gradlew assembleDebug
```

L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

Le code du jeu est dans `app/src/main/java/com/sautique/game/GameView.kt`.

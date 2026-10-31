# Sautique

Petit jeu Android en vue isométrique (effet 3D) : maintiens le doigt appuyé sur l'écran pour charger le saut,
relâche pour sauter. Plus l'appui est long, plus le personnage va loin.

- La plateforme de départ est fixe, une nouvelle plateforme apparaît plus loin.
- 1 saut réussi = 1 point (compteur en gros en haut de l'écran).
- Saut trop court sur ta propre plateforme : pas de point, tu peux réessayer.
- Tomber dans le vide = partie terminée. Le record est sauvegardé.
- Les plateformes (cubes ou cylindres) rétrécissent au fil des points et apparaissent
  tantôt vers la droite, tantôt vers la gauche.

## Pseudo et classements

- Au premier lancement, le jeu demande un **pseudo** (modifiable ensuite via le bouton ✎).
- **Top perso** : tes 10 meilleures parties, gardées sur le téléphone.
- **Top mondial** : le meilleur score de chaque joueur, partagé entre tous les téléphones,
  avec ta place au classement.

### Activer le top mondial (une seule fois)

Le classement mondial utilise [Supabase](https://supabase.com) (gratuit) :

1. Crée un compte puis un projet sur supabase.com.
2. Dans le projet : **SQL Editor** > **New query**, colle le contenu de
   `supabase/schema.sql`, puis **Run**.
3. Dans **Project Settings** > **API Keys** (ou **Data API**), récupère l'**URL du projet**
   (`https://xxxx.supabase.co`) et la clé publique (**publishable** ou **anon**).
   N'utilise jamais la clé *secret* / *service_role*.
4. Sur GitHub : **Settings** > **Secrets and variables** > **Actions** > **New repository secret**,
   crée `SUPABASE_URL` et `SUPABASE_ANON_KEY` avec ces deux valeurs.
5. Relance le workflow **Build APK** (onglet Actions > Run workflow) et installe le nouvel APK.

Tant que ce n'est pas configuré, le jeu marche normalement et le top mondial
affiche « Classement mondial non configuré ».

En local, tu peux aussi les mettre dans `~/.gradle/gradle.properties` :
`SUPABASE_URL=...` et `SUPABASE_ANON_KEY=...`.

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

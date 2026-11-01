# Publier Sautique sur le Play Store

Le projet produit deux versions de l'appli Android :

| | Version **GitHub** (APK) | Version **Play Store** (AAB) |
|---|---|---|
| Installation | fichier APK des releases GitHub | Play Store |
| Mises à jour | automatiques, depuis GitHub | par le Play Store |
| Classement mondial | ton serveur (VPS), pseudo choisi dans le jeu | **Google Play Jeux**, nom du profil Play Jeux |
| Fichier produit | `Sautique-X.Y.Z.apk` | `Sautique-X.Y.Z-playstore.aab` |

Les deux fichiers sont ajoutés à chaque release GitHub. La version web (iPhone) continue
d'utiliser le serveur : ses joueurs ne sont **pas** dans le classement Play Jeux.

---

## 1. Compte développeur

1. Créer le compte sur <https://play.google.com/console> (25 $ une fois, vérification d'identité).
2. **Créer l'application** : nom « Sautique », type **Jeu**, gratuit.

## 2. Premier envoi et clé de signature

1. *Tester et publier → Tests → Test interne* → créer une version → envoyer le fichier
   `Sautique-X.Y.Z-playstore.aab` (dernière release GitHub).
2. À la question sur la **signature d'application** : choisir **utiliser ma propre clé**
   (« Exporter et importer une clé à partir d'un keystore Java ») et suivre la procédure avec
   le fichier `sautique.jks` et son mot de passe.
   → Les APK GitHub et la version Play Store auront la même signature : tes amis pourront passer
   de l'un à l'autre **sans perdre** pièces, objets et records.
3. Noter l'**empreinte SHA-1** affichée dans *Configuration → Intégrité de l'appli → Signature
   d'application* (elle sert à l'étape 3).

## 3. Google Play Jeux (classements)

Dans la Play Console : *Croissance → Play Jeux → Configuration et gestion → Configuration*.

1. **Créer un projet Play Jeux** (« Non, mon jeu n'utilise pas encore les API Google »).
2. **Identifiants** → *Ajouter un identifiant* → type **Android** :
   - créer l'identifiant OAuth proposé (lien vers Google Cloud) avec le nom de paquet
     `com.sautique.game` et l'empreinte **SHA-1** de l'étape 2 ;
   - accepter l'écran de consentement OAuth (type « Externe », nom « Sautique », ton e-mail).
3. **Classements** → *Créer un classement* :
   - nom : `Meilleur score` (traductions possibles) ;
   - format : nombre entier, **ordre décroissant** (le plus grand est le meilleur) ;
   - limites (anti-triche) : minimum `1`, maximum `5000` par exemple.
4. **Testeurs** : ajouter les adresses Gmail des testeurs (sinon ils ne voient pas les
   classements tant que le jeu n'est pas publié).
5. **Publier** la configuration Play Jeux (*Révision et publication*).
6. Me donner (ou écrire dans `app/src/play/res/values/play_games.xml`) :
   - l'**ID du projet** Play Jeux (uniquement des chiffres) ;
   - l'**ID du classement** (commence par `Cgk…`).

   Tant que ces deux valeurs ne sont pas remplies, la version Play Store affiche
   « Classement mondial non configuré » (le jeu marche quand même).

## 4. Fiche et formulaires

- **Fiche Play Store** : description courte (80 caractères), description, icône 512×512,
  bannière 1024×500, au moins 2 captures d'écran (téléphone).
- **Politique de confidentialité** : URL obligatoire.
- **Sécurité des données** : avec Play Jeux, le jeu n'envoie rien à ton serveur ; les scores
  sont gérés par Google Play Jeux. Aucune pub réelle, aucun suivi.
- **Classification du contenu** : questionnaire IARC.
- **Public cible** : 13 ans et plus (conseillé).
- **Annonces** : « Non » (les fausses pubs sont un gag, pas de vraies publicités).

## 5. Test fermé puis production

Comptes personnels récents : **test fermé avec au moins 12 testeurs pendant 14 jours**, puis
demande d'accès à la production depuis le tableau de bord.

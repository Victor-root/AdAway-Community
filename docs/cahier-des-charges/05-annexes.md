# 05. Fonctions annexes

Périmètre : mise à jour intégrée de l'app, sauvegarde et restauration, commandes externes, tuile des réglages rapides, démarrage au boot et après mise à jour, notifications, scanner d'adwares, journal de diagnostic, batterie et constructeurs, initialisations au lancement, raccourcis du lanceur.

Identité de l'app à conserver pour rester compatible : identifiant de paquet `org.adaway`, nom affiché `AdAway` (le nom "AdAway Community" n'apparaît que dans l'écran À propos). Version actuelle de référence : nom `6.6.1-c`, code `60601` (règle : code = x.yy.zz du nom, le suffixe `-c` signifie "fork communautaire" et n'entre pas dans le code). Android minimum : 8.0 (API 26).

## 1. Mise à jour intégrée de l'application

### 1.1 Source des mises à jour

- Une seule source : les "releases" GitHub du dépôt `Victor-root/AdAway-Community`.
- Requête exacte : `GET https://api.github.com/repos/Victor-root/AdAway-Community/releases/latest`
  - en-tête `Accept: application/vnd.github+json`
  - en-tête `X-GitHub-Api-Version: 2022-11-28`
  - aucune authentification (limite GitHub de 60 requêtes par heure et par adresse IP).
- Ce point d'accès GitHub ne renvoie que la dernière release publiée, ni brouillon ni "pre-release". Il n'existe donc qu'un seul canal (stable). Aucun canal beta n'est géré par l'app.
- Champs lus dans la réponse JSON :
  - `tag_name` : version proposée (vide si absent).
  - `body` : notes de version en Markdown (vide si absent).
  - `assets[]` : on prend le premier élément dont `name` se termine par `.apk` (sans tenir compte de la casse) et dont `browser_download_url` n'est pas vide. Son URL est l'adresse de téléchargement.
- Conventions de publication constatées sur GitHub : tags sans préfixe `v` (`6.6.1-c`, `6.6.0-c`, `6.5.1-c`, `6.5.0-c`, plus un ancien `6.1.4-5`), fichier APK nommé `AdAway.Community.v<versionName>.apk` (ex. `AdAway.Community.v6.6.1-c.apk`). L'app ne dépend pas du nom de fichier, seulement de l'extension `.apk`.
- Le document RELEASING.md décrit encore le circuit de l'AdAway d'origine (XDA, `https://app.adaway.org/adaway.apk`, F-Droid, tags `vX.Y.Z` et `vX.Y.Zb` pour les beta). Ce circuit n'est PAS utilisé par l'app actuelle. À ne pas reprendre comme source.

### 1.2 Décision "mise à jour disponible"

Une mise à jour est déclarée disponible si les trois conditions sont réunies :
1. `tag_name` n'est pas vide ;
2. un fichier `.apk` a été trouvé dans les assets ;
3. la version du tag est strictement supérieure à la version installée (nom de version).

Si le code de version installé n'est pas lisible ou vaut 0 ou moins, aucune vérification n'est faite.

Comparaison des versions (règle actuelle) :
- On extrait de chaque chaîne toutes les suites de chiffres, dans l'ordre (tout le reste, `v`, points, tirets, `-c`, lettres, est ignoré). `6.5.1-c` donne 6,5,1. `v6.5.1` donne 6,5,1. `6.5.10-c` donne 6,5,10.
- On compare segment par segment ; un segment absent vaut 0 (`6.5` = `6.5.0`).
- Une chaîne sans aucun chiffre vaut 0.
- Conséquence : le suffixe `-c` est neutre. Mais tout chiffre placé dans un suffixe compte comme un segment supplémentaire (`6.1.4-5` donne 6,1,4,5, donc plus récent que `6.1.4-c`).

### 1.3 Moments où la vérification a lieu

| Moment | Condition | Réglage (clé, défaut) |
|---|---|---|
| Ouverture de l'écran d'accueil (mobile ou TV), uniquement lors d'un vrai démarrage de l'écran (pas après une rotation) | réglage activé | "Vérifier la présence d'une mise à jour au démarrage" (`updateCheckAppStartup`, vrai) |
| Tâche de fond périodique, toutes les 24 h | réglage activé | "Vérifier régulièrement la présence de mises à jour" (`updateCheckAppDaily`, vrai) |
| Bouton "Vérifier maintenant" des réglages | toujours | aucun |

Tâche périodique :
- planifiée (ou laissée telle quelle si elle existe déjà) à chaque démarrage du processus de l'app si le réglage est actif, annulée sinon ;
- replanifiée immédiatement quand l'utilisateur active le réglage, annulée quand il le désactive ;
- aucune contrainte réseau ni batterie ;
- si une mise à jour est trouvée, elle affiche la notification "mise à jour de l'app disponible" (voir 6). Elle ne télécharge rien.
- nom unique de la tâche dans le planificateur Android : `ApkUpdateWork`.

### 1.4 Où l'utilisateur voit la mise à jour

- Accueil mobile : une carte bannière "Update available" / "Tap to install %1$s" (FR : "Mise à jour disponible" / "Touchez pour installer %1$s", %1$s = tag). Visible seulement si une mise à jour est disponible. Un appui ouvre l'écran de mise à jour. Un appui sur le numéro de version affiché sur l'accueil ouvre aussi cet écran.
- Accueil TV : un bouton "Install version %1$s" (FR "Installer la version %1$s"), visible seulement si mise à jour disponible, qui ouvre l'écran de mise à jour.
- Réglages > Mises à jour > "Update now" / "Install version %1$s" (FR "Mettre à jour" / "Installer la version %1$s") : entrée masquée par défaut, visible dès qu'une mise à jour est connue, ouvre l'écran de mise à jour.
- Réglages > "Check now" (FR "Vérifier maintenant") : l'entrée se grise pendant la requête, puis un message court (toast long) :
  - "New version available: %1$s" (FR "Nouvelle version disponible : %1$s") ;
  - sinon "No update available" (FR "Aucune mise à jour disponible").

### 1.5 Écran de mise à jour

Titre de l'écran : "AdAway update" (FR "Mise à jour d'AdAway"). Fond rouge (même style que l'accueil de bienvenue), logo en haut.

Contenu :
- En-tête :
  - si mise à jour disponible : "Update available" (FR "Une mise à jour est proposée") et bouton "Update now" (FR "Mettre à jour maintenant") visible ;
  - sinon : "You are up-to-date" (FR "AdAway est à jour"), sans bouton.
- Titre de section "Last changes" (FR "Derniers changements"), puis le texte `body` de la release rendu en Markdown (titres, gras, listes, liens, emojis), dans une zone qui défile.
- L'écran n'effectue lui-même aucune vérification : il affiche le dernier résultat connu en mémoire.

Appui sur "Update now" :
1. le bouton disparaît ;
2. tout ancien APK téléchargé est supprimé (on retélécharge toujours) ;
3. une barre de progression 0 à 100 apparaît ;
4. téléchargement de l'APK via le gestionnaire de téléchargements Android, sans notification système visible, titre interne "AdAway <tag>", description "Downloading latest AdAway version…" (FR "Téléchargement de la version d'AdAway la plus récente…"), type `application/vnd.android.package-archive`, destination : cache externe privé de l'app, fichier `adaway-community-update.apk` ;
5. pendant le téléchargement, l'état est relu toutes les 100 ms : texte "<téléchargé> / <total>" en tailles lisibles (ex. "3,2 Mo / 9,8 Mo"), barre au pourcentage. Pas d'affichage tant que la taille totale est inconnue ;
6. à la fin : barre à 100 % et texte "Complete" (FR "Terminée"), puis passage à l'installation.

### 1.6 Installation et permission "installer des applis inconnues"

- Si l'app n'a pas le droit d'installer des applications (Android 8+) : l'app mémorise "installation en attente" (clé `updateInstallPending` = vrai dans les préférences) puis ouvre l'écran système "Installer des applis inconnues" pour `org.adaway`.
- Au retour sur l'écran de mise à jour, ou sur l'accueil mobile ou TV (cas où Android a tué l'app pendant que l'utilisateur était dans les réglages, fréquent sur Android TV / Shield) :
  - si une installation est en attente, le drapeau est effacé ;
  - si la permission est maintenant accordée, l'APK est vérifié puis installé ;
  - si elle est refusée, l'écran de mise à jour retire la barre et le texte de progression ; l'utilisateur peut relancer depuis le bouton.
- Vérification obligatoire avant toute installation (le fichier téléchargé est considéré comme non fiable, car le cache externe est accessible à d'autres apps sur Android 8 à 10) :
  1. le fichier doit être un paquet Android lisible ;
  2. il doit déclarer le paquet `org.adaway` ;
  3. son ensemble de certificats de signature doit être exactement égal à celui de l'app installée (sur Android 9+, signataires actuels si plusieurs signataires, sinon historique de la signature ; avant Android 9, signatures classiques) ;
  4. toute erreur ou information manquante = refus.
  En cas de refus, le fichier est supprimé et rien n'est installé.
- Si la vérification passe : copie de l'APK dans le cache interne privé (`verified-update.apk`), suppression de la copie externe, puis ouverture de l'installateur système sur la copie privée (partage via le fournisseur de fichiers d'autorité `org.adaway.fileprovider`, lecture seule accordée à l'installateur). Android demande toujours la confirmation de l'utilisateur.
- Un téléchargement terminé en échec ou interrompu n'est jamais transmis à l'installateur : les fichiers sont supprimés.

### 1.7 Nettoyage

- À chaque démarrage du processus de l'app, les deux fichiers (`adaway-community-update.apk` dans le cache externe, `verified-update.apk` dans le cache interne) sont supprimés, sauf si une installation est en attente (drapeau 1.6).

### 1.8 Après l'installation d'une nouvelle version

Voir 5.2 (message "AdAway updated / Relaunch the app").

## 2. Sauvegarde et restauration

### 2.1 Accès

Réglages > "Backup & restore" (FR "Sauvegarde et restauration"), deux entrées :
- "Backup" / "Backup to external storage your block rules" (FR "Sauvegarde" / "Sauvegardez vos règles de blocage sur une mémoire externe") ;
- "Restore" / "Restore your block rules from backup file" (FR "Restaurer" / "Restaurez vos règles de blocage d'un fichier de sauvegarde").

### 2.2 Sauvegarde (export)

- Ouvre le sélecteur de fichiers système en mode "créer un document", type `application/json`, nom proposé `adaway-backup.json`. L'utilisateur choisit l'emplacement et peut renommer. Annulation = rien ne se passe.
- Le travail se fait en arrière-plan, puis un toast long :
  - succès : "Backup successfully exported to file '%s' on your external storage." (FR "La sauvegarde a été exportée avec succès vers le fichier « %s » de votre mémoire externe.") ;
  - échec : "Failed to export backup file '%s'." (FR "Échec d'exportation du fichier de sauvegarde « %s ».").
  - %s = dernier segment du chemin de l'adresse du document choisi (souvent le nom du fichier, parfois un identifiant technique selon le fournisseur de stockage).

Contenu exact sauvegardé :
- toutes les sources de hosts, SAUF la source interne "liste de l'utilisateur", triées par libellé ;
- toutes les règles personnelles de l'utilisateur (liste bloquée, autorisée, redirigée), y compris celles désactivées.

NON sauvegardé : les préférences de l'app, les dates de mise à jour des sources, les tailles, les identifiants de cache HTTP, les hôtes téléchargés depuis les sources, le journal DNS, le journal de diagnostic. (Le commentaire du manifeste affirme que les préférences sont exportables : c'est faux dans le code.)

### 2.3 Format du fichier (contrat à conserver à l'identique)

Fichier texte UTF-8, un objet JSON indenté de 4 espaces, quatre clés dans cet ordre :

```json
{
    "sources": [
        {
            "label": "StevenBlack Unified hosts",
            "url": "https:\/\/raw.githubusercontent.com\/StevenBlack\/hosts\/master\/hosts",
            "enabled": true,
            "allow": false,
            "redirect": false
        }
    ],
    "blocked": [
        { "host": "ads.example.com", "enabled": true }
    ],
    "allowed": [
        { "host": "*.example.org", "enabled": false }
    ],
    "redirected": [
        { "host": "tracker.example.net", "redirect": "192.168.1.10", "enabled": true }
    ]
}
```

Champs d'une source :
- `label` (texte) : nom affiché ;
- `url` (texte) : adresse `https://...` ou `content://...` (fichier local choisi par l'utilisateur) ;
- `enabled` (booléen) : source active ;
- `allow` (booléen) : la source peut fournir des règles "autorisé" ;
- `redirect` (booléen) : la source peut fournir des règles "redirection".

Champs d'une règle utilisateur :
- `host` (texte) : nom d'hôte, jokers `*` et `?` admis ;
- `redirect` (texte, facultatif) : adresse IP de redirection ; écrit seulement s'il est non vide ;
- `enabled` (booléen).

Remarques de format :
- Il n'y a AUCUN champ de version dans le fichier. Le type de liste est donné uniquement par la clé (`blocked`, `allowed`, `redirected`).
- La bibliothèque JSON actuelle écrit les `/` échappés en `\/`. Le lecteur doit accepter les deux formes (c'est du JSON standard).
- Ce format est hérité de l'AdAway d'origine (versions 5 et 6), mêmes noms de clés. Aucun autre format (ancien AdAway 3/4) n'est reconnu.

### 2.4 Restauration (import)

- Ouvre le sélecteur de fichiers système en mode "ouvrir un document" (fichiers ouvrables seulement), avec filtre de type :
  - Android 8.0 et 8.1 : tous les fichiers (`*/*`) ;
  - Android 9 : `application/json` et `application/octet-stream` ;
  - Android 10+ : `application/json` uniquement.
- Lecture du fichier en arrière-plan, puis toast long :
  - succès : "Backup successfully imported from your external storage." (FR "La sauvegarde a été importée avec succès de votre mémoire externe.") ;
  - échec : "Failed to import backup. Is its format correct? Check logcat for more details." (FR "Échec d'importation de la sauvegarde. Le format est-il correct ? Consultez logcat pour plus de précisions.").
- Règles de lecture :
  - les quatre tableaux `sources`, `blocked`, `allowed`, `redirected` sont obligatoires ; s'il en manque un, ou si le JSON est invalide, l'import est déclaré en échec ;
  - traitement dans l'ordre : sources, puis bloqués, puis autorisés, puis redirigés ;
  - une entrée invalide est ignorée sans faire échouer le reste ;
  - validation d'une source : `label`, `url`, `enabled`, `allow`, `redirect` obligatoires ; le libellé et l'URL ne doivent contenir ni retour chariot ni saut de ligne (protection contre l'injection de lignes dans le fichier hosts) ; l'URL doit être `https://` suivi d'au moins un caractère, ou une URL `content://` ;
  - validation d'une règle : `host` et `enabled` obligatoires ; `host` doit être un nom de domaine valide une fois les jokers retirés ou remplacés par une lettre ; `redirect`, s'il est présent, doit être une adresse IPv4 ou IPv6 valide.
- Fusion (pas de remplacement) :
  - une source dont l'URL existe déjà est ignorée (la source existante garde ses réglages) ; les nouvelles sont ajoutées ;
  - une règle utilisateur dont le nom d'hôte existe déjà dans la liste utilisateur est mise à jour (type, état, redirection écrasés par ceux du fichier), sinon elle est ajoutée ;
  - rien n'est supprimé.
- Une source de type fichier local (`content://`) est réimportée sans le droit de lecture : elle ne pourra pas être lue tant que l'utilisateur ne la resélectionne pas.
- Après l'import, rien n'est re-téléchargé ni réappliqué automatiquement.

### 2.5 Sauvegarde Android automatique

- Désactivée volontairement : aucune donnée de l'app ne part vers la sauvegarde Google ni vers le transfert d'appareil à appareil (Android 12+). Seule la sauvegarde manuelle ci-dessus existe. À conserver.

## 3. Commandes externes (Tasker, Automate, etc.)

### 3.1 Contrat (valeurs exactes)

- Type : broadcast.
- Action : `org.adaway.action.SEND_COMMAND`
- Extra texte : clé `COMMAND`, valeurs `START` ou `STOP` (sensible à la casse).
- Composant récepteur actuel : `org.adaway/org.adaway.broadcast.CommandReceiver`. Comme le récepteur est déclaré dans le manifeste, l'envoyeur doit adresser le broadcast au paquet `org.adaway` (ou au composant) : Android 8+ ne livre pas les broadcasts implicites aux récepteurs déclarés.
- Permission exigée de l'envoyeur : `org.adaway.permission.SEND_COMMAND`, niveau "dangerous" (l'app tierce doit la déclarer et l'utilisateur doit l'accorder).
  - Libellé : "Send commands to AdAway" / description "Allow to send commands to AdAway like enabling or disabling the system-wide ad-blocking" (FR "Envoyer des commandes à AdAway" / "Permettre d'envoyer des commandes à AdAway telles qu'activer ou désactiver le blocage des publicités pour le système entier").
  - Groupe : `org.adaway.permission-group.API`, libellé "AdAway permissions" / "Allow to interact with AdAway" (FR "Autorisations d'AdAway" / "Permettre d'interagir avec AdAway").

### 3.2 Effet

- `START` : active le blocage avec la méthode choisie.
  - Mode root : régénère le fichier hosts à partir des règles déjà en base et l'installe (pas de téléchargement des sources).
  - Mode VPN : enregistre que l'utilisateur veut le VPN actif, puis démarre le VPN s'il ne tourne pas.
- `STOP` : désactive le blocage.
  - Mode root : remet le fichier hosts d'origine.
  - Mode VPN : enregistre que l'utilisateur ne veut plus le VPN, puis l'arrête.
- Valeur absente ou inconnue : rien (simple trace dans le journal).
- Méthode non choisie (premier lancement non terminé) : rien.
- Aucune réponse n'est renvoyée à l'envoyeur, aucun message à l'utilisateur, même en cas d'échec.
- Les actions "Pause" et "Resume" de la notification VPN utilisent ce même mécanisme (STOP et START), en interne.

## 4. Tuile des réglages rapides

- Libellé "AdAway", icône logo de l'app. Composant actuel `org.adaway.tile.AdBlockingTileService` (si ce nom change, la tuile déjà posée par l'utilisateur disparaît après la mise à jour).
- État : "active" si le blocage est appliqué, "inactive" sinon. L'état suit en direct le blocage tant que le panneau est ouvert.
- Appui : bascule. Si appliqué, désactive (même effet que `STOP`) ; sinon active (même effet que `START`). Un deuxième appui pendant une bascule en cours est ignoré. À la fin, quelle que soit l'issue, la tuile se recale sur l'état réel.
- Aucun message en cas d'échec. Méthode non choisie : l'appui ne fait rien.

## 5. Démarrage au boot et après mise à jour de l'app

### 5.1 Au démarrage du téléphone (fin du boot)

- Mode root : si le serveur web local est activé (`webserverEnabled`, défaut faux), il est démarré. Le fichier hosts, lui, reste en place sans action.
- Mode VPN : si "Activer au démarrage" est actif (`vpnOnBoot`, défaut vrai) :
  1. l'intention utilisateur "VPN voulu" est forcée à vrai (même si l'utilisateur l'avait arrêté avant le redémarrage) ;
  2. si l'autorisation VPN Android manque, l'app tente d'ouvrir la fenêtre d'autorisation ;
  3. puis lance le VPN.
- Rien n'est fait au déverrouillage anticipé (mode "Direct Boot") ni pour les variantes constructeur du signal de démarrage.

### 5.2 Après remplacement de l'app par une nouvelle version

- Au signal "mon paquet a été remplacé" : l'app note l'heure.
- 5 secondes plus tard, si aucun écran d'accueil (mobile ou TV) ne s'est ouvert entre-temps :
  - toast long "AdAway updated: Relaunch the app" (FR "AdAway mis à jour: Relancez l'app") ;
  - notification "AdAway updated" / "Relaunch the app" (FR "AdAway mis à jour" / "Relancez l'app") sur le canal "App updates", qui ouvre l'app (bon écran d'accueil selon mobile ou TV) ; non affichée si les notifications sont coupées.
- But : contourner le bouton "Ouvrir" de l'installateur qui échoue sans rien dire sur Android TV.
- Le VPN n'est pas relancé immédiatement après la mise à jour : il revient soit à l'ouverture de l'app, soit au prochain passage du contrôle périodique du VPN (toutes les 15 min, réseau requis). Le serveur web root n'est pas relancé non plus.

## 6. Notifications

### 6.1 Canaux (identifiants exacts, créés à chaque démarrage)

| Identifiant | Nom EN / FR | Description EN | Importance |
|---|---|---|---|
| `UpdateChannel` | "Hosts updates" / "Mises à jour des hôtes" | "Notification when newer hosts source files are available." | basse |
| `AppUpdateChannel` | "App updates" / "Mises à jour de l'app" | "Notification when a new version of the app is available. Mute this channel to silence app-update alerts without affecting other notifications." | basse |
| `VpnServiceChannel` | "VPN" / "RPV/VPN" | "VPN running notifications" | basse |

Style commun : petite icône logo, couleur rouge `#b71c1c` (colorisée), pas d'heure affichée.

### 6.2 Notifications émises

| N° | Canal | Titre / texte (EN, FR) | Quand | Appui |
|---|---|---|---|---|
| 10 | Hosts | "Source update available" / "Newer hosts files available for download." (FR "Une mise à jour des sources est proposée" / "Des fichiers hosts plus récents peuvent être téléchargés.") | tâche de fond des sources : des mises à jour existent et la synchro automatique est désactivée | ouvre l'accueil, disparaît |
| 11 | App | "App update available" / "A new version of AdAway is available for download." (FR "Une mise à jour de l'appli est proposée" / "Une nouvelle version d'AdAway est proposée en téléchargement.") | tâche quotidienne de vérification de l'app, si une version plus récente existe | ouvre l'écran de mise à jour, disparaît |
| 12 | App | "AdAway updated" / "Relaunch the app" | 5 s après une mise à jour de l'app si elle n'a pas été ouverte | ouvre l'app, disparaît |
| 20 | VPN | "Ad-blocker VPN %1$s" (FR "RPV/VPN bloqueur de publicités %1$s") avec état "starting"/"active" | VPN en démarrage ou actif (notification permanente du service) | ouvre l'accueil ; bouton "Pause" quand actif |
| 21 | VPN | même titre, état "stopping", "wait for network", "reconnecting", "reconnecting error", "stopped" (FR "arrêt", "en attente du réseau", "reconnexion", "erreur de reconnexion", "arrêté") | autres états du VPN | ouvre l'accueil ; bouton "Resume" (FR "Reprendre") quand arrêté |

- Les n° 10, 11, 12 respectent l'interrupteur global des notifications de l'app (rien n'est affiché s'il est coupé).
- Les n° 10, 11, 12 sont effacées à chaque ouverture d'un écran d'accueil.
- Le détail du cycle de vie de la notification VPN relève du cahier VPN.

### 6.3 Permission de notifier (Android 13+)

- Demandée pendant l'assistant de premier lancement, à l'étape de synchronisation : à la fin de la synchro, ou au plus tard 10 s après l'arrivée sur l'étape.
- Réglages > Mises à jour : une entrée "Notifications are disabled" / "Application notifications won't show. Tap to enable them." (FR "Les notifications sont désactivées" / "Les notifications de l'appli ne seront pas affichées. Touchez pour les activer.") apparaît seulement si la permission n'est pas accordée ; elle ouvre les réglages de notifications de l'app. L'état est réévalué à chaque retour sur l'écran.

## 7. Scanner d'adwares

### 7.1 Fonctionnement constaté

- Liste toutes les applications installées (permission de voir tous les paquets) et, pour chacune, ses activités, récepteurs et services.
- Une app est signalée si au moins un de ces composants a un nom qui commence par l'un de ces préfixes (sensible à la casse) :
  `com.airpush.`, `com.adnotify.`, `com.appbucks.sdk.`, `com.appenda.`, `com.applovin.`, `com.iac.notification.`, `com.inmobi.`, `com.Leadbolt.`, `cn.kuguo.`, `com.sellaring.`, `com.senddroid.`, `com.tapjoy.`
- Résultat : liste triée par nom d'app puis nom de paquet, chaque ligne montrant le nom de l'app et son paquet.
- Textes : titre "Adware", description "Here you can find installed adware, bad applications which can't be blocked by AdAway. These apps use for example Airpush notifications that pop up even when the app is not running or even change your ringtone. The only available countermeasure is to uninstall those apps by clicking on the list items.", pendant l'analyse "Scanning…", résultat vide "No Adware were found!".
- Appui sur une ligne : ouvre la demande de désinstallation système de cette app.

### 7.2 Statut réel

- L'écran du scanner n'est accessible depuis AUCUN menu de l'app actuelle (l'entrée "Scan for adware" n'existe plus que dans une liste de textes inutilisée).
- En revanche, l'analyse est lancée en arrière-plan à chaque ouverture de l'écran de mise à jour, sans que son résultat soit affiché.

## 8. Journaux

### 8.1 Journal de diagnostic (exportable)

- Emplacement de l'interface : Réglages > section VPN (section grisée en mode root). Trois entrées :
  - interrupteur "Record diagnostic log" (clé `vpnDiagnosticLogEnabled`, défaut faux), résumé "Turn on before reproducing a problem, then export it below. May slightly slow down older devices. Records only technical VPN events, never browsing history or personal data." ; prise d'effet immédiate, sans redémarrage, et conservée après redémarrage de l'app ;
  - "Diagnostic log" / "View, copy or share the recent errors and VPN events. Useful when reporting a problem." : ouvre une boîte de dialogue ;
  - "Clear diagnostic log" / "Delete the recorded events. Do this before reproducing a problem to capture a clean trace." : efface tout, toast court "Diagnostic log cleared.".
- Ce qui est enregistré : tous les messages de niveau information, avertissement, erreur et assertion de toute l'app (pas seulement le VPN), uniquement quand l'interrupteur est actif. Les niveaux debug et verbeux ne sont jamais écrits.
- Format d'une ligne : `MM-jj HH:mm:ss N/étiquette: message` (N = I, W, E ou A ; heure locale ; l'étiquette est souvent vide). La pile d'erreur, s'il y en a une, suit sur les lignes suivantes.
- Filtrage de confidentialité :
  - les traces de résultat de chaque requête DNS (bloqué, autorisé, redirigé) ne sont jamais écrites ;
  - les traces d'échec de requête DNS sont gardées mais le nom d'hôte est remplacé par `(host hidden)`, sans la pile d'erreur. Préfixes concernés : "Failed to get inet address for host ", "Failed to query DNS Name ", "No address was found for DNS Name ".
- Stockage : deux fichiers dans le stockage privé de l'app, `diagnostic.log` (courant) et `diagnostic.log.1` (précédent). Quand le fichier courant atteint 100 Kio, il devient le précédent (l'ancien précédent est supprimé). Maximum environ 200 Kio. Les écritures ne bloquent jamais l'app ; une erreur d'écriture est ignorée.
- Lecture : précédent puis courant (ordre chronologique), en arrière-plan.
- Boîte de dialogue :
  - titre "Diagnostic log" ; zone de texte défilante de hauteur fixe, les boutons restent toujours visibles ;
  - si vide : "No events have been recorded yet. Turn on "Record diagnostic log" above, then try to reproduce the problem." et seulement "OK" ;
  - sinon : aperçu limité aux 20 000 derniers caractères (précédé de "…" si tronqué), boutons "Share", "Copy", "OK" ;
  - "Copy" copie le journal COMPLET dans le presse-papiers, toast "Diagnostic log copied to clipboard." ;
  - "Share" ouvre le sélecteur de partage Android, texte brut, sujet et titre "Share diagnostic log", contenu = journal complet.
- Traductions FR existantes pour tous ces textes ("Enregistrer le journal de diagnostic", "Journal de diagnostic", "Effacer le journal de diagnostic", "Partager", "Copier", etc.).

### 8.2 Journal technique de développement

- Sur une version de développement, ou si le réglage caché "Verbose logging" (`debugEnabled`, défaut faux, "App restart is needed to apply") est actif : tous les messages vont aussi dans le journal système Android, avec les traces détaillées des commandes root.
- La section "Debug" des réglages n'est visible que sur une version de développement.
- En version publiée, rien n'est écrit dans le journal système.

## 9. Batterie et constructeurs

Uniquement dans l'assistant de premier lancement mobile (étapes 3 et 4 sur 4, après le choix de la méthode et la synchro). Absent de l'assistant TV. Jamais bloquant : "Suivant" est toujours possible.

### 9.1 Étape "optimisation de batterie Android"

- Titre "Stay protected in the background", texte "Android can pause AdAway's VPN in the background to save power, which can silently stop ad blocking. Allow AdAway to ignore battery optimization so it keeps running." (FR "Restez protégé en arrière-plan", etc.).
- Bouton "Allow" (FR "Autoriser") : ouvre la demande système directe d'exemption pour `org.adaway` ; si le téléphone ne la propose pas, ouvre la liste générale des optimisations de batterie ; si rien n'existe, ne fait rien.
- Si l'exemption est déjà accordée (vérifié à l'affichage, au retour de la demande, et à chaque retour sur l'écran) : bouton grisé "Allowed ✓" (FR "Autorisé ✓").
- Note de bas : "You can always change this later from" + chemin "Android Settings › Apps › AdAway › Battery".

### 9.2 Étape "gestionnaire de batterie du constructeur"

- Titre "One more thing: your phone's own battery manager", texte "Some phone brands have their own battery manager that can also block AdAway. Here's what to check on yours:", avertissement "These are general pointers, not an exact guide: the wording and location can vary by phone model and Android version.".
- Bouton "Open app info" : ouvre la fiche système de l'app. En cas d'impossibilité : toast "Could not open the app info screen. Open it manually from Android Settings → Apps → AdAway.".
- Détection de la marque : le fabricant ou la marque déclarés par l'appareil (en minuscules) contiennent l'un des mots-clés suivants, premier groupe trouvé :

| Groupe | Mots-clés | Chemin affiché | Consigne | Consigne secondaire |
|---|---|---|---|---|
| Samsung (One UI) | samsung | App info › Battery | Look for something like "Background usage limits" › "Never sleeping apps", then add AdAway to that list. | Also check for an "Auto optimization" setting and turn off "Restart when needed" if present. |
| Xiaomi / Redmi / POCO (MIUI / HyperOS) | xiaomi, redmi, poco | App info › Battery saver | Choose "No restrictions". | Also enable "Autostart" ("Background autostart" on newer versions). |
| Oppo / OnePlus / Realme (ColorOS) | oppo, oneplus, realme | App info › Battery usage | Choose "Allow background activity" (sometimes shown as "No restrictions"). | Also look for an "Auto-launch" toggle for AdAway (often under Settings › Apps), so it can restart itself after being force-stopped. |
| Huawei / Honor (EMUI / MagicOS) | huawei, honor | App info › Battery | Look for "App launch", turn off "Manage automatically", then enable all 3: "Auto-launch", "Secondary launch" and "Run in background". | Also lock AdAway in the Recents screen (swipe down on its card until a padlock appears). |
| vivo / iQOO (Funtouch / OriginOS) | vivo, iqoo | App info › Battery usage | Look for a background power option and choose "Don't restrict background power usage" (not "Smart control"). | Also look for an "Autostart" setting for AdAway, often under Settings › Applications. |
| Other brand (défaut) | aucun | pas de chemin | Look in app info for a battery or background activity setting and make sure AdAway is not restricted. | aucune |

Toutes ces chaînes existent en français dans les ressources actuelles.

### 9.3 Récupération après arrêt forcé par le constructeur

- À chaque ouverture de l'accueil (mobile ou TV), en mode VPN, si l'utilisateur veut le VPN actif et que le service ne tourne pas, le VPN est relancé (détail dans le cahier VPN).

## 10. Initialisations au lancement du processus

Dans cet ordre, à chaque création du processus de l'app (y compris en arrière-plan : boot, tâche planifiée, tuile, commande) :
1. Journalisation : le journal de diagnostic est branché (il n'écrit que si l'interrupteur est actif) ; journal système activé seulement en développement ou si `debugEnabled`.
2. Création (ou mise à jour) des trois canaux de notification.
3. Sources : planification de la tâche de vérification des sources si `updateCheckHostsDaily` (défaut vrai) : toutes les 6 h, premier passage 3 h après la planification, réseau requis (réseau non facturé à l'usage seulement si `updateOnlyOnWifi`, défaut faux), stockage non saturé ; nom unique `HostsUpdateWork` ; une tâche déjà planifiée est conservée telle quelle. Sinon la tâche est annulée.
4. Mise à jour de l'app : planification ou annulation de la tâche quotidienne `ApkUpdateWork` (voir 1.3), puis nettoyage des APK (voir 1.7).

Le thème (clair, sombre, suivre le système) est appliqué à l'ouverture de chaque écran, pas au démarrage du processus.

## 11. Raccourcis du lanceur (appui long sur l'icône)

Trois raccourcis statiques :
- `preferences` : "Preferences", ouvre les réglages ;
- `dns_requests` : "DNS requests", ouvre le journal des requêtes DNS ;
- `your_lists` : "Your lists", ouvre les listes de l'utilisateur.

## 12. Valeurs à reprendre pour la migration depuis l'ancienne app

La nouvelle app remplacera l'ancienne sur les appareils (même paquet `org.adaway`, même clé de signature, sinon la mise à jour intégrée la refusera). Pour cette zone :
- fichier de préférences `preferences`, clés : `updateCheckAppStartup` (vrai), `updateCheckAppDaily` (vrai), `updateCheck` (vrai), `updateCheckHostsDaily` (vrai), `automaticUpdateDaily` (vrai), `updateOnlyOnWifi` (faux), `vpnOnBoot` (vrai), `webserverEnabled` (faux), `vpnDiagnosticLogEnabled` (faux), `debugEnabled` (faux), `adBlockMethod` (entier : 0 non choisi, 1 root, 2 VPN), `vpnUserEnabled`, drapeaux techniques `updateInstallPending` et `updateInstallToastTimestamp` ;
- tâches planifiées existantes : `ApkUpdateWork`, `HostsUpdateWork`, `vpnHeartbeat` (à annuler et replanifier au premier lancement de la nouvelle app, car elles visent l'ancien code) ;
- canaux de notification : `UpdateChannel`, `AppUpdateChannel`, `VpnServiceChannel` (garder les mêmes identifiants pour conserver les choix de l'utilisateur, par exemple un canal mis en sourdine) ;
- composants visibles de l'extérieur : récepteur de commandes, tuile, autorité `org.adaway.fileprovider`.

## Défauts ou bizarreries constatés

- **La tâche quotidienne ne voit presque jamais la mise à jour** : elle lance la vérification puis lit le résultat immédiatement, alors que ce résultat n'est publié qu'un peu plus tard. Dans un processus démarré pour l'occasion, la valeur lue est vide et aucune notification n'apparaît. Dans un processus déjà vivant, elle lit l'ancien résultat. La vérification de fond doit utiliser directement le résultat qu'elle vient d'obtenir.
- **Échec réseau présenté comme "à jour"** : "Vérifier maintenant" affiche "No update available" quand la requête échoue (pas de réseau, limite GitHub, erreur). Il faut un message d'erreur distinct.
- **L'écran de mise à jour ne vérifie rien lui-même** : ouvert depuis la notification après la mort du processus, ou si la vérification au démarrage est désactivée, il affiche "You are up-to-date" avec des notes vides, même si une version existe.
- **Échec de téléchargement non signalé** : si le téléchargement échoue, l'écran reste avec la barre de progression et sans bouton, sans message. De même, si aucune adresse d'APK n'est connue, le suivi tourne sans fin.
- **Deux chemins d'installation concurrents** : à la fin du téléchargement, l'installation est lancée à la fois par l'écoute système "téléchargement terminé" et par l'écran de suivi. Selon l'ordre, l'installateur peut s'ouvrir deux fois, ou s'ouvrir alors que l'écran a envoyé l'utilisateur vers la permission d'installation. Il faut un seul chemin.
- **Refus de signature silencieux** : un APK refusé (signature différente) est supprimé sans aucun message à l'utilisateur, qui ne comprend pas pourquoi rien ne se passe.
- **Tâche quotidienne sans contrainte réseau** : elle se réveille même hors connexion et échoue sans rien dire.
- **Notification de mise à jour répétée chaque jour** pour la même version, sans mémoire "déjà notifié".
- **Comparaison de versions fragile** : les chiffres d'un suffixe comptent (`6.1.4-5` passe devant `6.1.4-c`, un futur `6.7.0-c2` serait vu comme différent de `6.7.0-c`). Un segment trop grand pour être lu est simplement retiré, ce qui décale les segments suivants (le commentaire prétend qu'il vaut 0).
- **Pas de canal beta** alors que RELEASING.md parle de beta ; et RELEASING.md décrit un circuit obsolète (XDA, app.adaway.org, F-Droid, tags `vX.Y.Z`).
- **Scanner d'adwares orphelin** : inaccessible depuis l'interface, mais exécuté pour rien à chaque ouverture de l'écran de mise à jour (balayage complet des apps installées). Liste de préfixes figée et très ancienne.
- **Sauvegarde incomplète par rapport à ce qui est annoncé** : le manifeste dit que les préférences sont exportables, ce n'est pas le cas. Les textes parlent de "external storage" alors que l'utilisateur choisit n'importe quel emplacement.
- **Import partiel sans retour arrière** : si un tableau manque en fin de fichier, les sources et listes déjà traitées restent importées, mais l'utilisateur voit "échec".
- **Import trop permissif sur la cohérence** : une règle "redirigée" sans adresse est acceptée, une règle "bloquée" avec adresse aussi. Une règle existante change silencieusement de liste si le fichier la range ailleurs.
- **Message d'échec d'import trompeur** : il renvoie vers "logcat" alors qu'en version publiée rien n'y est écrit.
- **Nom de fichier affiché après export** parfois illisible (identifiant technique du fournisseur de stockage au lieu du nom).
- **Aucun numéro de version dans le format de sauvegarde** : impossible de faire évoluer le format proprement.
- **Commandes et tuile sans retour** : en cas d'échec (root refusé, VPN non autorisé), l'utilisateur et l'app appelante ne sont pas prévenus. Le travail est lancé en arrière-plan depuis un récepteur de broadcast sans demander au système de garder le processus en vie, il peut donc être interrompu.
- **Le bouton "Pause" de la notification VPN arrête complètement le VPN** et enregistre "VPN non voulu" : c'est un arrêt, pas une pause (le libellé "paused" existe mais n'est jamais utilisé).
- **Deux notifications VPN simultanées possibles** : pendant "wait for network" ou "reconnecting", la notification permanente "active" (n° 20) reste affichée en plus de la n° 21.
- **Boot en mode VPN** : l'option "Activer au démarrage" écrase le choix de l'utilisateur qui avait arrêté le VPN avant de redémarrer. Et la fenêtre d'autorisation VPN est ouverte depuis l'arrière-plan, ce qu'Android 10+ bloque ; le démarrage du VPN est tenté juste après sans attendre.
- **Après une mise à jour de l'app, le VPN n'est pas relancé tout de suite** (jusqu'à 15 min sans blocage, ou jusqu'à l'ouverture de l'app). Même chose pour le serveur web en root.
- **Message post-installation fragile** : il repose sur un délai de 5 s tenu par un processus qui peut être tué entre-temps.
- **Journal de diagnostic inaccessible en mode root** : ses réglages sont dans la section VPN, grisée en root, alors qu'il enregistre aussi les événements root.
- **Promesse de confidentialité partiellement tenue** : certaines traces de niveau information gardent du contenu DNS (ex. "Discarding DNS packet with no query" suivi du paquet complet). Le filtrage par début de message est fragile : tout nouveau message contenant un nom d'hôte fuirait.
- **Partage du journal par texte direct** : jusqu'à environ 200 Kio passés dans l'intention de partage, ce qui peut dépasser la limite de taille des échanges Android sur certains appareils. Un fichier partagé serait plus sûr.
- **Étapes batterie montrées aussi en mode root** alors que leur texte ne parle que du VPN. Et elles ne sont plus accessibles après l'assistant (aucune entrée dans les réglages).
- **Demande de permission de notifier lancée par un minuteur de 10 s** qui ne vérifie pas que l'écran est toujours affiché (risque de plantage si l'utilisateur a quitté l'étape).
- **L'entrée "Notifications are disabled"** ne vérifie que la permission Android 13 : sur Android 8 à 12, ou si l'utilisateur coupe les notifications autrement, elle n'apparaît jamais.
- **Textes inutilisés** : "Update available!", "Updating AdAway", "Changelog", liste "drawer_items", "vpn_notification_paused".

## Questions ouvertes

1. Faut-il proposer un canal beta (releases GitHub marquées "pre-release") avec un réglage dédié, ou garder un seul canal stable ?
2. Quelle règle de version adopter : ignorer explicitement tout suffixe après le dernier nombre "x.y.z" (ex. `-c`), ou adopter un vrai SemVer ? Que faire du tag historique `6.1.4-5` ?
3. Faut-il vérifier en plus un condensat (SHA-256) publié dans la release, en complément de la signature ?
4. La vérification quotidienne doit-elle exiger le réseau, voire un réseau non facturé ?
5. Le format de sauvegarde doit-il gagner un champ `version` (et les préférences) tout en restant lisible par l'ancien format ? Faut-il proposer "remplacer" en plus de "fusionner" ? Faut-il réappliquer le blocage automatiquement après une restauration ?
6. Une restauration doit-elle être tout ou rien (transaction) ?
7. Garder le niveau "dangerous" pour la permission de commande (l'utilisateur l'accorde à Tasker) ? Ajouter une commande `TOGGLE`, une commande de synchro des sources, et un broadcast de retour d'état pour les automatisations ?
8. Le scanner d'adwares est-il à supprimer (fonction morte, permission sensible "voir toutes les apps" qu'il est seul à justifier avec les exclusions VPN) ou à remettre dans un menu avec une liste de détection à jour ?
9. Le démarrage au boot doit-il respecter le dernier choix de l'utilisateur (VPN arrêté avant redémarrage) plutôt que forcer le VPN ?
10. Faut-il relancer immédiatement le VPN (et le serveur web root) après une mise à jour de l'app ?
11. Le journal de diagnostic doit-il être accessible dans les deux modes (section générale des réglages) et exporté en fichier plutôt qu'en texte ?
12. Les étapes batterie doivent-elles être sautées en mode root, et accessibles plus tard depuis les réglages ou l'aide ?
13. Le bouton de notification VPN doit-il être une vraie pause temporaire ou un arrêt (et le libeller en conséquence) ?
14. Faut-il garder les mêmes noms de composants techniques (récepteur de commandes, tuile, écran d'accueil du lanceur) pour ne pas casser les automatisations, la tuile posée et l'icône d'accueil des utilisateurs existants ? (Recommandé.)

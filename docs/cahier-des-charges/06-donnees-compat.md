# 06. Données de l'ancienne app et compatibilité de mise à jour

Ce document décrit uniquement les données laissées sur l'appareil par l'ancienne app (Java + C, version 6.6.1-c) et tout ce que la nouvelle app doit respecter pour s'installer par-dessus sans perte. Il ne décrit pas la logique de l'app.

Toutes les informations viennent de la lecture du code de la branche `main` (version 6.6.1-c). Quand un point n'a pas pu être vérifié dans le code, c'est indiqué.

## 1. Identité de l'app

### 1.1 Identifiants

| Élément | Valeur actuelle | Source |
|---|---|---|
| Package (namespace) | `org.adaway` | `app/build.gradle`, `namespace 'org.adaway'` |
| applicationId | `org.adaway` | Aucun `applicationId` explicite dans `defaultConfig` : Gradle reprend donc le namespace. Aucun flavor, aucun `applicationIdSuffix` (même en debug). |
| versionName | `6.6.1-c` | `gradle/libs.versions.toml`, clé `appName` |
| versionCode | `60601` | `gradle/libs.versions.toml`, clé `appCode` |
| minSdk | 26 (Android 8.0) | `app/build.gradle` |
| targetSdk | 33 (Android 13) | `app/build.gradle` |
| compileSdk | 34 | `app/build.gradle` |
| Nom affiché | `@string/app_name` | manifeste |
| Nom de session VPN | `AdAway` | `VpnBuilder.setSession("AdAway")`, visible dans les réglages VPN d'Android |

### 1.2 Convention de version

- Commentaire dans `libs.versions.toml` : `appCode` se lit `x.yy.zz` pour correspondre au nom. Exemple : `6.6.1-c` donne `60601`, `6.6.0-c` donnait `60600`.
- Le suffixe `-c` signifie "community fork" et est ignoré par le code.
- `RELEASING.md` demande la gestion sémantique des versions (depuis la version 4) et la mise à jour des deux clés avant chaque sortie.
- Tags Git attendus : `vX.Y.Z` (ou `vX.Y.Zb` pour une pré-version).
- Nom du fichier APK produit : `AdAway.Community.v<versionName>.apk` (bloc `applicationVariants` dans `app/build.gradle`). `RELEASING.md` mentionne encore l'ancien format `AdAway-<version_name>-<yyyymmdd>.apk` (incohérence de doc).

Conséquences pour la nouvelle app :
- Son versionCode doit être strictement supérieur à `60601`, sinon Android refuse la mise à jour.
- Si on garde la convention, une version 7.0.0 donnerait `70000`.

### 1.3 Signature

Ce que l'on sait, et seulement cela :
- `RELEASING.md` ne dit rien de la clé de signature. Il dit seulement de construire la variante `release`.
- `app/build.gradle` lit un fichier `keystore.properties` à la racine du projet (non versionné) avec les clés `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- Si ce fichier existe, la variante `release` ET la variante `debug` sont signées avec la même clé. Un utilisateur peut donc avoir installé une build debug signée avec la clé officielle : elle aura la même identité.
- Le CI GitHub (`.github/workflows/android-ci.yml`) ne signe pas (référence à un `app-release-unsigned.apk` en commentaire).
- Le programme de mise à jour intégré (`ApkInstaller.isSignedLikeInstalledApp`) refuse tout APK téléchargé dont le package n'est pas `org.adaway` ou dont l'ensemble des certificats n'est pas strictement égal à celui de l'app installée (comparaison d'ensembles, avec l'historique de certificats sur Android 9 et plus).

Conséquence : la nouvelle app doit être signée avec exactement la même clé. Une rotation de clé (schéma v3) casserait la comparaison d'ensembles faite par l'ancien programme de mise à jour.

### 1.4 Chemin de mise à jour depuis l'ancienne app

L'ancien programme de mise à jour (`UpdateModel` + `Manifest`) interroge :
`https://api.github.com/repos/Victor-root/AdAway-Community/releases/latest`

Il lit dans la réponse JSON :
- `tag_name` : comparé au versionName installé par `VersionCompare` (seuls les groupes de chiffres comptent, `v7.0.0` donne `[7,0,0]`, `6.6.1-c` donne `[6,6,1]`).
- `assets[].name` : le premier fichier dont le nom finit par `.apk` (insensible à la casse).
- `assets[].browser_download_url` : l'URL de téléchargement.
- `body` : le texte du changelog affiché.

Pour que les utilisateurs actuels reçoivent la nouvelle app par ce canal, la release GitHub doit donc : être la "latest" de ce dépôt, avoir un tag numériquement supérieur à 6.6.1, avoir un seul APK (ou l'APK voulu en premier), et cet APK doit avoir le même package et la même signature.

## 2. Base de données Room

### 2.1 Fichier et version

- Nom du fichier : `app.db` (dans le dossier standard des bases : `/data/data/org.adaway/databases/app.db`).
- Fichiers annexes probables : `app.db-wal` et `app.db-shm`. Room active le mode WAL par défaut (pas de `setJournalMode` dans le code), sauf sur les appareils à faible mémoire.
- Version actuelle : **7** (`@Database(..., version = 7)`).
- La version est stockée dans `PRAGMA user_version`.
- Identity hash Room de la version 7 : `dccd6211bcef97caed75ea42d7df1b32`, stocké dans la table `room_master_table` (ligne `id = 42`, colonne `identity_hash`).
- Tables techniques présentes en plus : `room_master_table`, `android_metadata` (ajoutée par Android), `sqlite_sequence` (à cause des clés `AUTOINCREMENT`).
- Pas de `fallbackToDestructiveMigration` : une base de version inconnue fait planter l'ouverture.
- Convertisseurs : `ListTypeConverter` (enum vers entier) et `ZonedDateTimeConverter` (date vers entier, secondes Unix en UTC).

### 2.2 Table `hosts_sources`

Une ligne par source de listes (URL ou fichier local), plus une ligne spéciale pour la liste de l'utilisateur.

SQL de création (v7) :
`CREATE TABLE hosts_sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, url TEXT NOT NULL, enabled INTEGER NOT NULL, allowEnabled INTEGER NOT NULL, redirectEnabled INTEGER NOT NULL, last_modified_local INTEGER, last_modified_online INTEGER, entityTag TEXT, size INTEGER NOT NULL)`

| Colonne | Type SQLite | Nul permis | Valeurs et sens |
|---|---|---|---|
| `id` | INTEGER, clé primaire auto-incrémentée | non | Identifiant. `1` est réservé à la liste utilisateur. |
| `label` | TEXT | non | Nom affiché de la source. |
| `url` | TEXT | non | `https://...` pour une source en ligne, `content://...` pour un fichier choisi via le sélecteur Android (SAF). Toute autre forme est traitée comme "non supportée". Unique. |
| `enabled` | INTEGER (booléen 0/1) | non | Source active (1) ou non (0). Défaut côté code : 1. |
| `allowEnabled` | INTEGER (0/1) | non | 1 = les lignes "autorisées" de cette source sont importées. Défaut : 0. |
| `redirectEnabled` | INTEGER (0/1) | non | 1 = les lignes avec une IP autre que 127.0.0.1, 0.0.0.0 ou ::1 sont importées comme redirections. Défaut : 0. |
| `last_modified_local` | INTEGER | oui | Date de la dernière installation locale de la source, en **secondes** Unix UTC. NULL = jamais installée. |
| `last_modified_online` | INTEGER | oui | Date de dernière modification connue côté serveur ou fichier, en **secondes** Unix UTC. NULL = inconnue. |
| `entityTag` | TEXT | oui | ETag HTTP renvoyé par le serveur (ajouté en v7). NULL possible. |
| `size` | INTEGER | non | Nombre de lignes de `hosts_lists` de cette source. 0 tant que non synchronisée. |

Index : `index_hosts_sources_url` UNIQUE sur `url`.

Une source est considérée "à jour" si `last_modified_online <= last_modified_local` et "en retard" si `last_modified_online > last_modified_local` (requêtes `countUpToDate` et `countOutdated`).

Pour les sources `content://`, l'ancienne app a pris une permission persistante de lecture (`takePersistableUriPermission` avec `FLAG_GRANT_READ_URI_PERMISSION`). Cette permission est liée au package et survit à la mise à jour. La nouvelle app peut donc relire ces URI sans redemander à l'utilisateur.

### 2.3 Entrée spéciale : la liste utilisateur

- `id = 1` (constante `USER_SOURCE_ID`).
- `url = content://org.adaway/user/hosts` (constante `USER_SOURCE_URL`). Ce n'est pas une vraie URI : aucun fournisseur de contenu `org.adaway` n'existe. C'est seulement un marqueur.
- Sur une installation neuve : `label` = texte traduit de `hosts_user_source` ("User list" en anglais, dans la langue du téléphone au moment de l'installation), `allowEnabled = 1`, `redirectEnabled = 1`, `enabled = 1`.
- Sur une base migrée depuis une ancienne version, le `label` et les drapeaux peuvent être différents (voir section 7).
- Elle est exclue de presque toutes les requêtes de l'app (`WHERE id != 1`), donc invisible dans la liste des sources et dans la sauvegarde.
- Toutes les règles ajoutées à la main par l'utilisateur ont `source_id = 1` dans `hosts_lists`.

### 2.4 Sources créées à la première installation

Créées par `AppDatabase.initialize` uniquement si la table est vide au moment de la création de la base :

| Ordre | label (traduit à l'installation) | url |
|---|---|---|
| 1 | `hosts_user_source` ("User list") | `content://org.adaway/user/hosts` (id 1) |
| 2 | `hosts_adaway_source` ("AdAway official hosts") | `https://adaway.org/hosts.txt` |
| 3 | `hosts_stevenblack_source` ("StevenBlack Unified hosts") | `https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts` |
| 4 | `hosts_peterlowe_source` ("Pete Lowe blocklist hosts") | `https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext` |

Les ids 2, 3, 4 ne sont pas garantis (auto-incrément). Ne pas s'y fier.

### 2.5 Table `hosts_lists`

Toutes les règles connues : celles de chaque source et celles de l'utilisateur.

SQL de création (v7) :
`CREATE TABLE hosts_lists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, host TEXT NOT NULL, type INTEGER NOT NULL, enabled INTEGER NOT NULL, redirection TEXT, source_id INTEGER NOT NULL, FOREIGN KEY(source_id) REFERENCES hosts_sources(id) ON UPDATE CASCADE ON DELETE CASCADE)`

| Colonne | Type | Nul permis | Valeurs et sens |
|---|---|---|---|
| `id` | INTEGER, clé primaire auto-incrémentée | non | Identifiant. |
| `host` | TEXT | non | Nom de domaine. Pour le type "autorisé", peut contenir les jokers `*` (n'importe quelle suite) et `?` (un caractère). Les règles "bloquées" venant des sources sont refusées si elles contiennent un joker. |
| `type` | INTEGER | non | `0` = BLOCKED (bloqué), `1` = ALLOWED (autorisé), `2` = REDIRECTED (redirigé). Toute autre valeur fait lever une exception à la lecture (`ListType.fromValue`). |
| `enabled` | INTEGER (0/1) | non | Règle active ou non. Désactiver une source met aussi toutes ses règles à 0 (`setSourceItemsEnabled`). |
| `redirection` | TEXT | oui | Adresse IP de redirection pour le type 2. NULL pour les règles des sources de type 0 ou 1. Pour les règles utilisateur, la valeur est celle passée par l'écran (NULL en pratique pour 0 et 1, voir questions ouvertes). |
| `source_id` | INTEGER | non | Référence `hosts_sources.id`. `1` = règle de l'utilisateur. |

Index :
- `index_hosts_lists_host` (non unique) sur `host`.
- `index_hosts_lists_source_id` (non unique) sur `source_id`.

Clé étrangère : `source_id` vers `hosts_sources.id`, avec suppression et mise à jour en cascade (supprimer une source supprime ses règles).

Règle non écrite par contrainte mais respectée par le code : une seule ligne utilisateur par `host` (`getHostId` cherche `host = ? AND source_id = 1` et met à jour la ligne existante au lieu d'en créer une nouvelle). Le même domaine peut donc changer de type mais pas exister deux fois pour l'utilisateur. Il peut en revanche exister plusieurs fois au total (une par source).

### 2.6 Table `host_entries`

Table dérivée, entièrement recalculée à chaque synchronisation (`HostEntryDao.sync` : vidage, import des bloqués, retrait des autorisés, ajout des redirections). Elle ne contient aucune donnée propre à l'utilisateur.

SQL de création (v7) :
`CREATE TABLE host_entries (host TEXT NOT NULL, type INTEGER NOT NULL, redirection TEXT, PRIMARY KEY(host))`

| Colonne | Type | Nul permis | Sens |
|---|---|---|---|
| `host` | TEXT, clé primaire | non | Domaine final à bloquer ou rediriger. |
| `type` | INTEGER | non | `0` bloqué ou `2` redirigé (les autorisés sont retirés). |
| `redirection` | TEXT | oui | IP pour le type 2. |

Index : `index_host_entries_host` UNIQUE sur `host` (redondant avec la clé primaire).

Elle peut être jetée et reconstruite par la nouvelle app à partir de `hosts_lists`. Le mode VPN de l'ancienne app lit directement cette table pour chaque requête DNS.

### 2.7 Historique des versions de schéma

Room est arrivé dans AdAway 4.0.5 (juillet 2018, d'après le CHANGELOG : "Change database to room, add migration from previous database"). Le code de migration depuis la base d'avant Room n'existe plus dans `main`. Toute version de schéma de 1 à 7 peut donc encore exister chez un utilisateur qui n'a pas mis à jour depuis longtemps.

| Version | Contenu | Correspondance probable (déduite du CHANGELOG, non certaine) |
|---|---|---|
| 1 | `hosts_sources(url PK, enabled, last_modified_local, last_modified_online)` ; `hosts_lists(host PK, type, enabled, redirection)` | 4.0.5 |
| 2 | Ajout de `id` auto-incrémenté aux deux tables, `source_id` dans `hosts_lists` avec clé étrangère, index unique sur `url` et sur `host` | 4.x ou 5.0 |
| 3 | Ajout d'une VUE `host_entries` | 5.0.x |
| 4 | L'index sur `hosts_lists.host` devient non unique, la vue est recréée avec `GROUP BY` | 5.0.x |
| 5 | La vue `host_entries` est remplacée par une TABLE `host_entries` | 5.0.x |
| 6 | Ajout de `label`, `allowEnabled`, `redirectEnabled`, `size` dans `hosts_sources` ; dates en ligne passées des millisecondes aux secondes | 5.0.7 ("Add hosts sources label and host counter", "Storage Access Framework support") |
| 7 | Ajout de `entityTag` dans `hosts_sources` | 6.0.x ("Improve hosts source cache to reduce bandwidth usage") |

Détail des migrations existantes (`db/Migrations.java`) à reproduire si on veut garder le support des vieilles bases :

- **1 vers 2** : recrée `hosts_sources` avec `id`, insère d'abord la liste utilisateur (`id = 1`, `url = USER_SOURCE_URL`, `enabled = 1`), puis copie les anciennes sources. Recrée `hosts_lists` avec `id` et `source_id`, en rattachant toutes les anciennes règles à la source 1. Crée les index.
- **2 vers 3** : crée la vue `host_entries`.
- **3 vers 4** : rend l'index `host` non unique, recrée la vue.
- **4 vers 5** : supprime la vue, crée la table `host_entries` et son index unique.
- **5 vers 6** : ajoute `label` (défaut `""`), `allowEnabled` (0), `redirectEnabled` (0), `size` (0). Met `label = url` partout. Remplace `url = "file://app/user/hosts"` par `content://org.adaway/user/hosts` avec `allowEnabled = 1`, `redirectEnabled = 1`. Donne leur nom aux trois sources par défaut (en anglais, en dur). Met `last_modified_local` à NULL (force une reconstruction). Divise `last_modified_online` par 1000 (millisecondes vers secondes). Supprime toutes les sources dont l'URL commence par `file://`.
- **6 vers 7** : ajoute `entityTag TEXT DEFAULT NULL`.

## 3. Préférences (SharedPreferences)

### 3.1 Fichier

- Nom : `preferences` (constante `Constants.PREFS_NAME`), en mode privé.
- Chemin : `/data/data/org.adaway/shared_prefs/preferences.xml`.
- Les écrans de réglages (`PrefsMainFragment`, `PrefsUpdateFragment`, `PrefsBackupRestoreFragment`) forcent ce même nom via `setSharedPreferencesName`. Aucun autre fichier de préférences de l'app n'a été trouvé dans le code (pas de `getDefaultSharedPreferences`).
- Un réglage n'est écrit dans le fichier que quand l'utilisateur le change ou quand l'écran de réglages l'affiche pour la première fois (les interrupteurs AndroidX enregistrent leur valeur par défaut à ce moment-là). Une clé absente doit donc toujours être lue avec la valeur par défaut ci-dessous.

### 3.2 Clés enregistrées

Toutes les clés sont définies dans `res/values/preferences.xml`, sauf mention contraire.

| Clé exacte | Type stocké | Défaut si absente | Sens |
|---|---|---|---|
| `darkThemeMode` | String | `MODE_NIGHT_FOLLOW_SYSTEM` | Thème. Valeurs : `MODE_NIGHT_NO` (clair), `MODE_NIGHT_YES` (sombre), `MODE_NIGHT_FOLLOW_SYSTEM` (système). Toute autre valeur est lue comme "système". Écrit aussi par le bouton thème de l'écran TV. |
| `adBlockMethod` | Int | `0` | Méthode de blocage : `0` = non définie (l'assistant d'accueil s'ouvre), `1` = ROOT (fichier hosts), `2` = VPN. Valeur inconnue lue comme 0. |
| `vpnPaused` | Int | `0` | Malgré son nom, c'est le dernier état connu du service VPN (`VpnStatus`) : `0` STOPPED, `10` STARTING, `11` RUNNING, `20` STOPPING, `21` WAITING_FOR_NETWORK, `22` RECONNECTING, `23` RECONNECTING_NETWORK_ERROR. En pratique seuls `0` et `11` sont écrits. Valeur inconnue lue comme 0. |
| `vpnUserEnabled` | Boolean | voir sens | Volonté de l'utilisateur d'avoir le VPN actif (différent de l'état réel). Si la clé est absente, la valeur est déduite de `vpnPaused` : vrai si `vpnPaused != 0`. Mis à vrai par une activation manuelle ou par le démarrage au boot, à faux par un arrêt manuel ou quand une autre app VPN prend la place. |
| `vpnOnBoot` | Boolean | `true` | Démarrer le VPN au démarrage du téléphone (seulement si `adBlockMethod = 2`). |
| `vpnWatchdog` | Boolean | `false` | "Monitor connection" : surveillance périodique du réseau pour relancer le VPN. |
| `vpnDiagnosticLogEnabled` | Boolean | `false` | Enregistrer le journal de diagnostic (fichier `diagnostic.log`). |
| `excludeSystemAppFromVpn` | String | `none` | Applis système exclues du VPN : `none` (aucune), `allExceptBrowsers` (toutes sauf les navigateurs et quelques paquets Google fixes), `all` (toutes). |
| `excludeUserAppFromVpn` | StringSet | ensemble vide | Noms de package des applis utilisateur exclues du VPN. |
| `redirectionIP` | String | `127.0.0.1` | IP de redirection IPv4 écrite devant chaque domaine bloqué (mode root). |
| `redirectionIPv6` | String | `::1` | IP de redirection IPv6 (mode root, seulement si `enableIpv6`). |
| `enableIpv6` | Boolean | `false` | Active IPv6 (lignes IPv6 dans le fichier hosts en root, prise en compte IPv6 en VPN). |
| `neverReboot` | Boolean | `false` | "Hide reboot dialog after apply". Enregistré mais plus lu nulle part (voir section 7). |
| `webserverEnabled` | Boolean | `false` | Serveur web local (mode root uniquement). Le défaut est passé à `false` en 6.6.1-c (CHANGELOG). |
| `webserverIcon` | Boolean | `true` | Le serveur web affiche l'icône de l'app à la place de la pub (option `--icon`). |
| `updateCheck` | Boolean | `true` | Vérifier les mises à jour des sources au lancement de l'app. |
| `updateCheckHostsDaily` | Boolean | `true` | Vérification périodique des sources (tâche de fond toutes les 6 h). |
| `automaticUpdateDaily` | Boolean | `true` | Installer automatiquement les sources mises à jour lors de la vérification périodique. |
| `updateOnlyOnWifi` | Boolean | `false` | La vérification périodique des sources n'a lieu que sur réseau non facturé. |
| `updateCheckAppStartup` | Boolean | `true` | Vérifier une mise à jour de l'app au lancement. |
| `updateCheckAppDaily` | Boolean | `true` | Vérification quotidienne d'une mise à jour de l'app. |
| `tvWelcomeDone` | Boolean | `false` | L'assistant Android TV a été terminé (ou l'install était déjà fonctionnelle avant son existence). |
| `debugEnabled` | Boolean | `false` | "Verbose logging". Catégorie cachée sur les builds release mais la valeur est toujours lue au démarrage. |
| `updateInstallPending` | Boolean | `false` | Défini dans `UpdateModel` (pas dans les ressources). Vrai pendant que l'utilisateur est dans l'écran Android "installer des applis inconnues" : empêche la suppression de l'APK téléchargé. Supprimé (pas mis à faux) une fois fini. |
| `updateInstallToastTimestamp` | Long | `0` | Défini dans `UpdateReceiver` (pas dans les ressources). Heure (ms) de la dernière mise à jour de l'app, sert à afficher un message 5 secondes après l'installation si l'app n'a pas été ouverte. Supprimé ensuite. |

### 3.3 Clés qui existent dans les ressources mais ne stockent rien

Ce sont des clés d'éléments d'écran (catégories ou boutons d'action). Elles n'apparaissent normalement pas dans le fichier.

- Catégories : `vpnAdBlockMethod`, `rootAdBlockMethod`, `debugCategory`.
- Actions : `openHosts`, `webserverTest`, `webserverCertificate`, `vpnReset`, `vpnDiagnosticLog`, `vpnDiagnosticLogClear`, `backup`, `restore`, `updateCheckNow`, `updateInstallNow`, `openNotificationPreferences`.
- Restes inutilisés : `customTarget` (défaut `/data/etc/hosts`) et la valeur `writeToSystem` (`pref_apply_method_def`, sans clé associée). Aucun code ne les lit. D'anciennes installations peuvent encore avoir `customTarget` dans leur fichier.

## 4. Autres fichiers stockés

| Emplacement | Nom | Contenu | Qui l'écrit | À reprendre ? |
|---|---|---|---|---|
| `files/` | `hosts` | Dernier fichier hosts généré (en-tête, localhost, toutes les entrées). Il n'est pas supprimé après copie. | `RootModel.createNewHostsFile` | Non (régénérable). Peut être supprimé. |
| `files/` | `default_hosts` | Fichier hosts minimal temporaire pour la désactivation. Supprimé juste après. | `RootModel.revertHostFile` | Non. |
| `files/` | `diagnostic.log` et `diagnostic.log.1` | Journal de diagnostic texte UTF-8, une ligne par événement (`horodatage lettre-de-niveau ...`). Rotation à 100 Ko, donc 200 Ko maximum. | `DiagnosticLog` | Optionnel. Peut être relu pour ne pas perdre l'historique, ou effacé. |
| `files/webserver/` | `localhost-2410.crt`, `localhost-2410.key`, `icon.svg`, `test.html` | Copie des fichiers de `assets/` pour le serveur web. Recopiée à chaque démarrage du serveur. | `WebServerUtils.inflateResources` | Non (vient de l'APK). |
| `cache/` | `dns_log.txt` | Sortie brute de tcpdump (requêtes DNS, mode root). | `TcpdumpUtils` | Non. |
| `cache/` | fichiers `journal` et `<hash>.0` / `<hash>.1` | Cache HTTP OkHttp des sources (100 Mo max) ET cache du client DNS-over-HTTPS (10 Mo max), dans le même dossier racine du cache. | `SourceModel`, `DohPacketProxy` | Non. |
| `cache/` | `verified-update.apk` | Copie vérifiée de l'APK de mise à jour, partagée à l'installeur via FileProvider. | `ApkInstaller` | Non, à supprimer. |
| Cache externe (`Android/data/org.adaway/cache/`) | `adaway-community-update.apk` | APK téléchargé par le DownloadManager. | `UpdateModel` | Non, à supprimer. |
| `databases/` | `app.db` (+ `-wal`, `-shm`) | Base principale (section 2). | Room | **Oui, obligatoire.** |
| `databases/` (géré par WorkManager) | base interne de WorkManager | Tâches planifiées (section 5.6). Le nom exact du fichier dépend de la version de WorkManager (non vérifié dans `main`). | WorkManager | Non, mais les tâches doivent être annulées ou remplacées. |
| `shared_prefs/` | `preferences.xml` | Section 3. | App | **Oui, obligatoire.** |
| Système | `/system/etc/hosts` | En mode root, fichier hosts installé. Sa première ligne commence par `# This hosts file has been generated by AdAway on: ` suivie de la date `yyyy-MM-dd HH:mm:ss`. C'est le seul indicateur de l'état "actif" en root. | `RootModel` via `dd`, `chown 0:0`, `chmod 644` | Oui, à reconnaître (section 6.2). |

Sauvegarde utilisateur (fichier choisi par l'utilisateur, nom proposé `adaway-backup.json`) : ce fichier n'est pas dans l'espace de l'app, mais la nouvelle app doit savoir le relire. Format JSON :
- `sources` : tableau d'objets `{label, url, enabled, allow, redirect}` (booléens pour les trois derniers). La liste utilisateur n'y figure pas.
- `blocked`, `allowed`, `redirected` : tableaux d'objets `{host, enabled, redirect?}`. `redirect` n'est écrit que s'il est non vide. Seules les règles utilisateur (`source_id = 1`) sont exportées.
- Aucune préférence n'est exportée.

Android Backup : `allowBackup="false"` et `data_extraction_rules.xml` excluent tout (cloud et transfert d'appareil). Il n'y a donc aucune restauration automatique sur laquelle s'appuyer.

## 5. Composants visibles de l'extérieur (manifeste)

### 5.1 Activités

| Classe complète | Exportée | Intent-filter / rôle |
|---|---|---|
| `org.adaway.ui.home.HomeActivity` | oui | `MAIN` + `LAUNCHER`, `launchMode="singleTop"`, porte les raccourcis (`meta-data android.app.shortcuts`). C'est l'icône du lanceur. |
| `org.adaway.ui.home.TvHomeActivity` | oui | `MAIN` + `LEANBACK_LAUNCHER`, bannière `@drawable/tv_banner`. Icône Android TV. |
| `org.adaway.ui.lists.ListsActivity` | non | Filtre `android.intent.action.SEARCH` (recherche interne), extra `org.adaway.lists.tab` (0, 1, 2). |
| `org.adaway.ui.log.LogActivity` | non (pas d'attribut, pas de filtre) | Cible d'un raccourci. |
| `org.adaway.ui.prefs.PrefsActivity` | non | Cible d'un raccourci. |
| Autres (`TvLogActivity`, `TvListsActivity`, `TvHostsSourcesActivity`, `TvSourceEditActivity`, `WelcomeActivity`, `TvWelcomeActivity`, `HostsSourcesActivity`, `SourceEditActivity`, `HelpActivity`, `PrefsVpnExcludedAppsActivity`, `UpdateActivity`) | non | Internes. `SourceEditActivity` et sa version TV lisent l'extra `sourceId`. |

### 5.2 Services

| Classe | Exporté | Permission | Filtre | Remarque |
|---|---|---|---|---|
| `org.adaway.vpn.VpnService` | oui | `android.permission.BIND_VPN_SERVICE` | `android.net.VpnService` | Service VPN. Reçoit l'extra String `COMMAND` = `START` ou `STOP`. |
| `org.adaway.tile.AdBlockingTileService` | oui | `android.permission.BIND_QUICK_SETTINGS_TILE` | `android.service.quicksettings.action.QS_TILE` | Tuile des réglages rapides, icône `@drawable/logo`, libellé `app_name`. Pas de mode "tuile active". |

### 5.3 Récepteurs

| Classe | Exporté | Permission | Action |
|---|---|---|---|
| `org.adaway.broadcast.BootReceiver` | oui | aucune | `android.intent.action.BOOT_COMPLETED` |
| `org.adaway.broadcast.CommandReceiver` | oui | `org.adaway.permission.SEND_COMMAND` | `org.adaway.action.SEND_COMMAND` avec extra String `COMMAND` = `START` (activer le blocage) ou `STOP` (désactiver). Utilisé aussi par les boutons Pause / Reprendre de la notification VPN. API publique pour Tasker, etc. |
| `org.adaway.broadcast.UpdateReceiver` | oui | aucune | `android.intent.action.MY_PACKAGE_REPLACED` |

### 5.4 Fournisseur

- `androidx.core.content.FileProvider`, autorité `org.adaway.fileprovider`, non exporté, `grantUriPermissions="true"`, chemins `cache-path "internal_cache" "."` et `external-cache-path "external_cache" "."`.

### 5.5 Permissions

Déclarées par l'app :
- Groupe `org.adaway.permission-group.API`.
- Permission `org.adaway.permission.SEND_COMMAND`, `protectionLevel="dangerous"`, dans ce groupe. Les applis tierces qui l'ont obtenue gardent l'autorisation tant que la nouvelle app la déclare avec le même nom.

Demandées :
`INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `REQUEST_INSTALL_PACKAGES`, `DOWNLOAD_WITHOUT_NOTIFICATION`, `QUERY_ALL_PACKAGES`.

Features optionnelles (`required="false"`) : `android.hardware.touchscreen`, `android.hardware.telephony`, `android.software.leanback`.

Les bibliothèques (WorkManager, androidx.startup, profileinstaller) ajoutent leurs propres composants et permissions au manifeste fusionné. Le manifeste fusionné n'est pas disponible dans `main`, la liste exacte n'est donc pas vérifiée.

### 5.6 Tâches planifiées WorkManager (persistées entre les versions)

| Nom unique | Classe du worker enregistrée | Fréquence |
|---|---|---|
| `HostsUpdateWork` | `org.adaway.model.source.SourceUpdateService$HostsSourcesUpdateWorker` | 6 h, délai initial 3 h, réseau connecté ou non facturé selon `updateOnlyOnWifi` |
| `ApkUpdateWork` | `org.adaway.model.update.ApkUpdateService$ApkUpdateWorker` | 1 jour |
| `vpnHeartbeat` (tag `VPN-heartbeat`) | `org.adaway.vpn.VpnServiceHeartbeat` | 15 min, réseau connecté |

WorkManager enregistre le nom de classe du worker dans sa base. Si la nouvelle app n'a plus ces classes, ces tâches échoueront à chaque déclenchement tant qu'elles ne sont pas annulées ou remplacées.

### 5.7 Canaux et identifiants de notification

| Canal | Identifiant | Importance |
|---|---|---|
| Mises à jour des sources | `UpdateChannel` | LOW |
| Mises à jour de l'app | `AppUpdateChannel` | LOW |
| VPN | `VpnServiceChannel` | LOW |

Identifiants de notification : 10 (sources), 11 (app), 12 (app installée), 20 (VPN en marche, notification de premier plan), 21 (VPN à reprendre). Les réglages faits par l'utilisateur sur un canal (muet, etc.) sont gardés par Android si l'identifiant du canal ne change pas.

### 5.8 Raccourcis statiques (`res/xml/shortcuts.xml`)

| shortcutId | Action | targetPackage | targetClass |
|---|---|---|---|
| `preferences` | `android.intent.action.VIEW` | `org.adaway` | `org.adaway.ui.prefs.PrefsActivity` |
| `dns_requests` | `android.intent.action.VIEW` | `org.adaway` | `org.adaway.ui.log.LogActivity` |
| `your_lists` | `android.intent.action.VIEW` | `org.adaway` | `org.adaway.ui.lists.ListsActivity` |

### 5.9 Schémas d'URL

Aucun. Aucune activité ne déclare de filtre `VIEW` avec un schéma ou un hôte. L'URI `content://org.adaway/user/hosts` n'est qu'un marqueur en base. Le seul hôte particulier côté réseau est `localhost` (configuration de sécurité réseau qui fait confiance aux certificats utilisateur pour `localhost` et ses sous-domaines, utilisée pour tester le serveur web sur `https://localhost/internal-test`).

### 5.10 Divers

- Diffusion locale (interne, pas exportée) : action `org.jak_linux.dns66.VPN_UPDATE_STATUS`, extra `VPN_STATUS`. Nom hérité de DNS66.
- Binaires natifs lancés en root, avec des noms qui se terminent par `.so` pour être installés par Android dans `nativeLibraryDir` (`useLegacyPackaging = true`) : `libwebserver_exec.so` (serveur web, ports 80 et 443) et `libtcpdump_exec.so`. Ils sont lancés en tâche de fond détachée et sont arrêtés par `killall <nom>`.

## 6. Ce que la mise à jour par-dessus doit garantir

### 6.1 Ce qu'Android conserve tout seul (si le package et la signature sont identiques)

- Tout le dossier privé `/data/data/org.adaway/` (base, préférences, fichiers, cache).
- Les permissions accordées (notifications, exclusion de l'optimisation batterie, installation d'applis inconnues).
- L'accord VPN donné par l'utilisateur (`VpnService.prepare` renvoie null), lié au package.
- Le réglage "VPN permanent" (always-on) d'Android : il est stocké par nom de package (`always_on_vpn_app`), pas par nom de classe.
- L'autorisation root accordée par Magisk ou équivalent : elle est en général liée à l'UID, qui ne change pas lors d'une mise à jour (comportement du gestionnaire root, non vérifiable dans `main`).
- Les permissions persistantes sur les fichiers `content://` des sources.
- Les permissions `SEND_COMMAND` accordées aux applis tierces, si la permission est toujours déclarée.
- Les réglages des canaux de notification, si les identifiants ne changent pas.
- La langue choisie par appli sur Android 13 et plus (gérée par le système, l'app ne la stocke pas).

### 6.2 Ce que la nouvelle app doit faire elle-même

**Méthode de blocage.** Lire `adBlockMethod`. Si la valeur vaut 1 ou 2, ne jamais réafficher l'assistant d'accueil. Sur TV, respecter aussi `tvWelcomeDone`.

**État du VPN.**
- La volonté de l'utilisateur est `vpnUserEnabled`, avec le repli sur `vpnPaused != 0` si la clé est absente. C'est cette valeur qui doit décider du redémarrage, pas `vpnPaused` seul (qui peut être faux après un arrêt brutal).
- Pendant la mise à jour, Android arrête le processus et donc le VPN. L'ancienne app ne redémarrait pas le VPN dans `UpdateReceiver` : seuls le "battement" WorkManager (15 min), l'ouverture de l'app ou le VPN permanent le relançaient. La nouvelle app devrait relancer le VPN dès `MY_PACKAGE_REPLACED` si `adBlockMethod = 2` et si la volonté utilisateur est vraie.
- Ne pas casser le VPN permanent : garder un service avec la permission `BIND_VPN_SERVICE` et le filtre `android.net.VpnService`. Android démarre ce service avec une intention qui porte l'action `android.net.VpnService` et sans extra `COMMAND` : la nouvelle app doit traiter ce cas comme un démarrage (voir section 7, l'ancienne app ne le faisait pas).
- Le statut stocké doit rester lisible par une éventuelle réinstallation de l'ancienne version (retour arrière) : continuer à écrire `vpnPaused` (0 ou 11) et `vpnUserEnabled` aide, mais ce n'est utile que si on veut permettre le retour arrière (question ouverte).

**État root.**
- L'état "actif" n'est pas stocké en préférence. Il est lu dans la première ligne de `/system/etc/hosts`. La nouvelle app doit reconnaître l'en-tête exact `# This hosts file has been generated by AdAway on: ` comme "géré par AdAway", sinon elle croira que le blocage est désactivé alors que le fichier installé bloque toujours.
- Les processus root de l'ancienne app continuent de tourner après la mise à jour (ils sont détachés) : `libwebserver_exec.so` peut toujours occuper les ports 80 et 443, et `libtcpdump_exec.so` peut toujours écrire dans `cache/dns_log.txt`. La nouvelle app doit les arrêter par ces noms exacts avant de lancer les siens, même si elle renomme ses propres binaires.
- Si `webserverEnabled` vaut vrai et que la méthode est root, l'ancienne app relance le serveur au démarrage du téléphone et à la création du modèle root.

**Composants à garder sous le même nom** (ou à remplacer par un `activity-alias` du même nom) :
- `org.adaway.ui.home.HomeActivity` : sinon l'icône posée sur l'écran d'accueil peut disparaître chez certains lanceurs.
- `org.adaway.ui.home.TvHomeActivity` : même raison sur TV.
- `org.adaway.tile.AdBlockingTileService` : la tuile ajoutée par l'utilisateur est liée au nom du composant. Si le nom change, la tuile disparaît.
- Les identifiants de raccourcis `preferences`, `dns_requests`, `your_lists` : les raccourcis épinglés par l'utilisateur sont désactivés si l'identifiant disparaît.
- `org.adaway.broadcast.CommandReceiver` n'a pas besoin de garder son nom de classe (les applis tierces visent l'action), mais l'action `org.adaway.action.SEND_COMMAND`, l'extra `COMMAND`, les valeurs `START` et `STOP` et la permission `org.adaway.permission.SEND_COMMAND` doivent rester identiques. À vérifier : si une appli tierce envoie une intention explicite vers la classe, le nom de classe compte aussi.

**Tâches WorkManager.** Au premier lancement, annuler les noms uniques `HostsUpdateWork`, `ApkUpdateWork` et `vpnHeartbeat` (ou les remplacer en les réenregistrant avec la nouvelle classe), pour ne pas laisser WorkManager tenter d'instancier des classes disparues.

**Canaux de notification.** Garder `UpdateChannel`, `AppUpdateChannel`, `VpnServiceChannel`, ou supprimer explicitement les anciens si on en crée de nouveaux, pour ne pas laisser de canaux orphelins dans les réglages Android.

**Valeurs par défaut.** Pour chaque clé absente, appliquer exactement le défaut de la section 3.2. Sinon un utilisateur qui n'a jamais ouvert les réglages verra son comportement changer (exemple : `vpnOnBoot` vaut vrai par défaut).

**targetSdk.** Si la nouvelle app vise Android 14 ou plus, Android impose un type de service de premier plan pour le service VPN, et la permission correspondante. Ce n'est pas un problème de données mais cela peut empêcher le VPN de redémarrer après la mise à jour s'il est mal déclaré (voir questions ouvertes).

## 7. Défauts ou bizarreries constatés

À ne pas reproduire dans la nouvelle app (mais à savoir lire) :

1. **Clé `vpnPaused` mal nommée.** Elle ne contient pas un booléen "en pause" mais un code entier d'état VPN (0 ou 11 en pratique).
2. **Démarrage par le VPN permanent ignoré.** Quand Android démarre le service via le VPN permanent, l'intention n'a pas d'extra `COMMAND`. `Command.readFromIntent` renvoie alors `UNKNOWN`, et `onStartCommand` ne démarre rien et renvoie `START_NOT_STICKY`. Le VPN permanent ne relance donc probablement pas l'ancienne app. Comme le système utilise un démarrage "au premier plan", l'absence d'appel à `startForeground` peut même provoquer une erreur. Lecture du code seulement, non testé sur appareil.
3. **Pas de relance du VPN après mise à jour.** `UpdateReceiver` n'affiche qu'un message, il ne relance ni le VPN ni le serveur web.
4. **Migration 1 vers 2 incohérente avec la 5 vers 6.** La 1 vers 2 insère la liste utilisateur avec la constante actuelle `content://org.adaway/user/hosts`. Or la 5 vers 6 attend l'ancienne adresse `file://app/user/hosts` pour poser `allowEnabled = 1` et `redirectEnabled = 1`. Une base partie de la version 1 garde donc une liste utilisateur avec `allowEnabled = 0`, `redirectEnabled = 0` et `label = content://org.adaway/user/hosts`. Une base partie des versions 2 à 5 garde `label = file://app/user/hosts` (le label est copié depuis l'URL avant le changement d'URL). Sans effet visible aujourd'hui car la liste utilisateur n'est jamais analysée comme une source et reste cachée, mais il ne faut pas se fier au label ni aux drapeaux de la ligne `id = 1`.
5. **Labels des sources par défaut figés à l'installation.** Sur une installation neuve, ils sont traduits dans la langue du moment. Après la migration 5 vers 6, ils sont en anglais en dur. Ils ne suivent jamais la langue actuelle.
6. **Schéma 4 mal exporté.** `4.json` et `5.json` ont le même identity hash et décrivent tous deux `host_entries` comme une table, alors que la migration 3 vers 4 crée une vue. Le JSON de la version 4 ne reflète pas la vraie base v4.
7. **Unités de date changeantes.** Avant la v6, `last_modified_online` était en millisecondes. Depuis, en secondes. `last_modified_local` a été remis à NULL lors de la migration.
8. **Index en double.** `index_host_entries_host` est unique sur une colonne qui est déjà la clé primaire.
9. **Unicité utilisateur assurée par le code seulement.** Rien dans le schéma n'empêche deux lignes utilisateur pour le même domaine. Les anciennes versions 2 avaient un index unique global sur `host`, supprimé en v4.
10. **Deux caches HTTP dans le même dossier.** Le cache des sources (100 Mo) et le cache DNS-over-HTTPS (10 Mo) utilisent tous les deux la racine de `cache/`. OkHttp ne prévoit pas deux caches sur le même dossier : risque de corruption mutuelle du fichier `journal`. `dns_log.txt` et `verified-update.apk` sont aussi dans ce même dossier.
11. **Fichier `files/hosts` jamais nettoyé.** Il reste sur le disque après chaque application, alors qu'il peut être gros.
12. **Préférence morte `neverReboot`.** L'interrupteur est affiché et enregistré, mais aucune partie du code ne le lit.
13. **Restes `customTarget` et `writeToSystem`.** Clé et valeur encore dans les ressources, plus utilisées.
14. **`debugEnabled` encore actif en release.** La catégorie est cachée en release, mais une valeur `true` déjà enregistrée (par une ancienne version où elle était visible) active toujours la journalisation détaillée au démarrage.
15. **Défaut `excludeUserAppFromVpn` déclaré comme chaîne vide** dans le XML alors que la valeur stockée est un ensemble de chaînes. Sans conséquence aujourd'hui (l'élément d'écran n'enregistre rien), mais trompeur.
16. **Sauvegarde trop permissive.** À l'import, une entrée du tableau `redirected` sans champ `redirect` est acceptée : elle donne une redirection avec IP nulle, qui s'écrit ensuite `null domaine` dans le fichier hosts. Inversement, une entrée `blocked` avec `redirect` garde cette IP. La sauvegarde ne contient pas les préférences, contrairement à ce que dit le commentaire du manifeste ("Preferences and rules can still be exported").
17. **Comparaison stricte des signatures.** `isSignedLikeInstalledApp` exige l'égalité des ensembles de certificats, ce qui rend toute rotation de clé impossible via le programme de mise à jour intégré.
18. **Nom de diffusion hérité.** L'action interne `org.jak_linux.dns66.VPN_UPDATE_STATUS` vient de DNS66. Le commentaire "nobody is listening" à côté est faux (`VpnModel` l'écoute).
19. **Doc de nommage d'APK périmée** dans `RELEASING.md` (voir 1.2).
20. **Pas de test de migration.** Aucun test `MigrationTestHelper` dans `androidTest`. Les migrations 1 à 7 n'ont jamais été vérifiées automatiquement.

## 8. Questions ouvertes

1. **Jusqu'où remonter ?** Faut-il garder la lecture des bases v1 à v6, ou accepter de ne gérer que la v7 (la seule produite par la branche Community depuis 6.0) ? La v1 date de 2018. Si on coupe, que fait-on d'une base trop vieille : on repart des sources par défaut en perdant la liste utilisateur, ou on tente une lecture "au mieux" ?
2. **Base d'avant Room.** Le nom et le format de la base d'AdAway 3.x / 4.0.4 ne sont plus dans `main`. Faut-il s'en soucier ? Probablement non.
3. **Clé de signature.** Quelle clé signe les releases GitHub de `Victor-root/AdAway-Community` ? Est-ce la même que celle des releases 6.5.x et 6.6.x installées chez les utilisateurs ? Les utilisateurs venant de l'AdAway d'origine (F-Droid ou adaway.org) ont-ils une signature différente, et donc pas de mise à jour possible par-dessus ?
4. **Garder Room ou non ?** Si la nouvelle app utilise Room avec exactement les mêmes entités, l'identity hash restera `dccd6211bcef97caed75ea42d7df1b32` et la base s'ouvrira telle quelle. Toute autre structure impose une migration 7 vers 8 (ou une lecture SQLite brute puis conversion).
5. **Retour arrière.** Veut-on permettre de réinstaller l'ancienne 6.6.1-c par-dessus la nouvelle (Android refuse un versionCode plus bas sans désinstallation, donc seulement via ADB) ? Si non, on peut changer librement les formats après conversion.
6. **Types de préférences hérités.** D'anciennes versions ont-elles stocké une de ces clés avec un autre type (par exemple `adBlockMethod` en chaîne) ? Le code actuel ferait alors une erreur de type à la lecture. Impossible à vérifier sans l'historique complet (historique ancien non vérifié).
7. **Règles utilisateur avec `redirection` vide.** Les écrans d'ajout passent la redirection telle quelle. Peut-il exister des lignes utilisateur de type 0 ou 1 avec une `redirection` chaîne vide plutôt que NULL ? Les écrans n'ont pas été lus en détail ici.
8. **Tâches WorkManager après mise à jour.** Android garde-t-il les tâches JobScheduler de WorkManager au remplacement du package sur toutes les versions visées ? Le code ne permet pas de le dire. Dans le doute, la nouvelle app doit réenregistrer ses tâches au premier lancement.
9. **Type de service de premier plan.** Si le targetSdk passe à 34 ou plus, quel `foregroundServiceType` déclarer pour le service VPN, et faut-il une permission supplémentaire ? À vérifier dans la documentation Android, pas dans `main`.
10. **Intentions explicites tierces.** Des applis tierces visent-elles `CommandReceiver` par son nom de classe ? Si oui, il faut garder `org.adaway.broadcast.CommandReceiver`.
11. **Journal de diagnostic.** Faut-il relire `diagnostic.log` pour le montrer dans la nouvelle app, ou le supprimer ?

## 9. Plan de reprise

Ordre conseillé au premier lancement de la nouvelle version, avant toute autre action. Chaque étape doit pouvoir être rejouée sans danger si l'app est tuée au milieu. Un marqueur "reprise terminée" (nouvelle clé de préférence, par exemple) n'est posé qu'à la fin.

### Étape 1. Préférences

- Lire `shared_prefs/preferences.xml` (nom `preferences`).
- Pour chaque clé de la section 3.2 : lire en tolérant un type inattendu (en cas d'erreur, prendre le défaut), appliquer le défaut si la clé est absente.
- Convertir vers le nouveau stockage :
  - `adBlockMethod` (0, 1, 2) vers "non choisi / root / VPN".
  - `vpnUserEnabled`, avec le repli sur `vpnPaused != 0`, vers "l'utilisateur veut le VPN actif".
  - `vpnPaused` : ne pas reprendre comme vérité, seulement comme indice pour le repli ci-dessus.
  - `darkThemeMode` vers "clair / sombre / système".
  - `excludeSystemAppFromVpn` (`none`, `allExceptBrowsers`, `all`) vers le choix équivalent.
  - `excludeUserAppFromVpn` vers une liste de noms de package (en ignorant ceux qui ne sont plus installés au moment de l'usage, pas au moment de la reprise).
  - `redirectionIP`, `redirectionIPv6` : reprendre tels quels après validation d'adresse IP. En cas de valeur invalide, garder le défaut.
  - Toutes les autres clés booléennes : reprendre telles quelles.
- Ne pas reprendre : `neverReboot` (mort), `customTarget`, `updateInstallPending`, `updateInstallToastTimestamp`, `debugEnabled` (à décider, mais il ne doit pas activer de journaux en release).
- Ne pas supprimer l'ancien fichier tant que la reprise n'est pas terminée.

### Étape 2. Base de données

- Ouvrir `databases/app.db` et lire `PRAGMA user_version`.
- Si la version vaut 7 : lire directement.
- Si la version est entre 1 et 6 : appliquer d'abord les migrations de la section 2.7 dans l'ordre (ou décider autrement, voir question 1).
- Si la version est supérieure à 7 ou si la base est illisible : ne rien effacer, signaler l'erreur, et proposer une restauration depuis une sauvegarde JSON.
- Convertir :
  - `hosts_sources` sauf `id = 1` vers les sources de la nouvelle app : `label`, `url`, `enabled`, `allowEnabled`, `redirectEnabled`. Les dates (`last_modified_local`, `last_modified_online`, secondes UTC), `entityTag` et `size` peuvent être reprises pour éviter un téléchargement complet, ou remises à zéro pour forcer une synchronisation propre.
  - `hosts_lists` avec `source_id = 1` vers les règles utilisateur : `host`, `type` (0 bloqué, 1 autorisé, 2 redirigé), `enabled`, `redirection`. C'est la donnée la plus précieuse : elle n'existe nulle part ailleurs.
  - `hosts_lists` avec `source_id != 1` : données téléchargées, régénérables. Les reprendre seulement si on veut que le blocage reste actif sans réseau juste après la mise à jour.
  - `host_entries` : ne pas reprendre, la recalculer.
  - Ignorer les drapeaux et le label de la ligne `id = 1` (section 7, point 4).
- Si la nouvelle app garde Room avec le même schéma : rien à convertir, juste continuer à ouvrir `app.db` en version 7.

### Étape 3. Fichiers

- Supprimer : `files/hosts`, `files/default_hosts`, `files/webserver/` (si la nouvelle app ne s'en sert pas), `cache/dns_log.txt`, `cache/verified-update.apk`, l'APK `adaway-community-update.apk` du cache externe, et le cache HTTP OkHttp à la racine de `cache/`.
- `files/diagnostic.log` et `.1` : garder ou supprimer selon la réponse à la question 11.

### Étape 4. Processus et tâches laissés par l'ancienne app

- En mode root (si l'accès root est disponible) : arrêter `libwebserver_exec.so` et `libtcpdump_exec.so` par leur nom.
- Annuler les tâches WorkManager `HostsUpdateWork`, `ApkUpdateWork`, `vpnHeartbeat`, puis enregistrer les nouvelles selon les préférences reprises.
- Supprimer ou réutiliser les canaux `UpdateChannel`, `AppUpdateChannel`, `VpnServiceChannel`.

### Étape 5. Remettre le blocage dans l'état où l'utilisateur l'avait laissé

- Mode VPN : si "l'utilisateur veut le VPN actif" est vrai et que l'accord VPN est toujours là, démarrer le VPN tout de suite (sans attendre l'ouverture de l'app). Si l'accord a disparu, ne rien forcer et le signaler à l'ouverture.
- Mode root : lire la première ligne de `/system/etc/hosts`. Si elle commence par `# This hosts file has been generated by AdAway on: `, considérer le blocage comme actif. Régénérer le fichier avec le nouveau moteur seulement après une synchronisation réussie, pour ne jamais installer un fichier vide. Si `webserverEnabled` est vrai, relancer le serveur web.

### Étape 6. Ce qui doit rester identique dans le nouveau manifeste

- Package `org.adaway`, même clé de signature, versionCode supérieur à 60601.
- Noms de composants (ou alias) : `org.adaway.ui.home.HomeActivity`, `org.adaway.ui.home.TvHomeActivity`, `org.adaway.tile.AdBlockingTileService`.
- Service VPN : permission `BIND_VPN_SERVICE` et filtre `android.net.VpnService`.
- Récepteur de commandes : action `org.adaway.action.SEND_COMMAND`, extra `COMMAND` (`START` / `STOP`), permission `org.adaway.permission.SEND_COMMAND` dans le groupe `org.adaway.permission-group.API`.
- Récepteurs `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED` (ce dernier sert à lancer la reprise et à relancer le VPN).
- Identifiants de raccourcis `preferences`, `dns_requests`, `your_lists`.

### Étape 7. Sauvegardes JSON

- Savoir relire le format de la section 4 (`sources`, `blocked`, `allowed`, `redirected`), en validant chaque entrée et en rejetant une redirection sans IP.
- Si la nouvelle app change de format, garder la lecture de l'ancien format pour les sauvegardes déjà faites par les utilisateurs.

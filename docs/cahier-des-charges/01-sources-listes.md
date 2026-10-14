# 01 : Sources de listes et règles de blocage

Ce document décrit le comportement observé dans l'ancienne application (référence fonctionnelle). Les points marqués "à ne pas reproduire" sont repris dans la section des défauts.

## 1. Vocabulaire

- **Source** : une liste distante (URL) ou un fichier local, fournie par un tiers, qui contient des noms d'hôte.
- **Entrée** : une ligne utile d'une source ou de la liste perso. Une entrée a un type : bloquée, autorisée ou redirigée (avec une adresse IP cible).
- **Liste perso** (liste de l'utilisateur) : les entrées saisies à la main par l'utilisateur. Techniquement c'est une source interne, jamais affichée dans l'écran des sources.
- **Synchroniser** : télécharger ou relire les sources qui ont changé, puis recalculer la liste finale.
- **Liste finale** : l'ensemble des noms d'hôte effectivement bloqués ou redirigés, utilisé par le mode root (fichier hosts) et par le mode VPN.
- **Appliquer** : pousser la liste finale vers le mécanisme de blocage actif (réécrire le fichier hosts en root, rafraîchir le VPN).

## 2. Sources par défaut et état initial

Au tout premier lancement (base vide), l'app crée :

| Ordre | Libellé EN | Libellé FR | Adresse | Format | Redirections | Activée |
|---|---|---|---|---|---|---|
| interne | User list | Liste de l'utilisateur | (interne) | bloque, autorise et redirige | oui | toujours |
| 1 | AdAway official hosts | Fichier hosts officiel d'AdAway | https://adaway.org/hosts.txt | Bloquer | non | oui |
| 2 | StevenBlack Unified hosts | Fichier hosts unifié de StevenBlack | https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts | Bloquer | non | oui |
| 3 | Pete Lowe blocklist hosts | Fichier hosts unifié de Pete Lowe | https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext | Bloquer | non | oui |

Règles :
- Les trois sources publiques sont **toutes activées** dès l'installation.
- Le libellé est figé dans la langue du téléphone au moment de l'installation (il n'est pas retraduit si la langue change).
- Rien n'est téléchargé à l'installation. Le premier téléchargement a lieu dans l'assistant d'accueil (étape "synchronisation") ou au premier "Synchroniser".
- Toute opération de lecture des sources attend que cette création initiale soit terminée, pour ne jamais conclure "aucune source" sur une installation neuve.
- Dans l'assistant d'accueil, une fois la première synchronisation réussie, l'app **réactive toutes les sources désactivées** et, si au moins une a changé, relance une synchronisation.

## 3. Types de sources

Le type est déduit uniquement du début de l'adresse enregistrée.

### 3.1 Source URL (HTTPS)
- Adresse commençant par `https://`. Le HTTP en clair est **refusé**.
- Téléchargée par une requête HTTP GET (voir section 6).

### 3.2 Fichier local
- Choisi via le sélecteur de documents système (tous types de fichiers acceptés, aucun filtre de type MIME).
- L'app demande une **permission de lecture persistante** sur le document choisi, pour pouvoir le relire après redémarrage.
- L'adresse enregistrée est l'identifiant de contenu renvoyé par le sélecteur (`content://...`). Les adresses `file://` ne sont pas acceptées.
- Si la permission est perdue plus tard (fichier supprimé, appli fournisseur désinstallée, permission révoquée), la lecture échoue et la source compte comme "en échec" pour cette synchronisation, sans plantage.

### 3.3 Sources hébergées sur Git (cas particulier d'une source URL)
L'app reconnaît trois hébergeurs par le **début exact** de l'adresse (sensible à la casse) :

| Hébergeur | Préfixe reconnu | Exemple |
|---|---|---|
| GitHub (fichier brut) | `https://raw.githubusercontent.com/` | `.../StevenBlack/hosts/master/hosts` |
| GitHub Gist | `https://gist.githubusercontent.com` | `.../PerfectSlayer/<id>/raw/<révision>/hosts` |
| GitLab | `https://gitlab.com/` | `.../quidsup/notrack-blocklists/raw/master/notrack-blocklist.txt` |

Ce que l'app en fait : **uniquement** obtenir la date de dernière modification du fichier via l'API publique de l'hébergeur, sans authentification. Le téléchargement du contenu reste un téléchargement HTTPS normal.

- GitHub : l'adresse est découpée en `propriétaire / dépôt / branche / chemin`. L'app demande le dernier commit qui touche ce chemin dans le dépôt, et prend la **date du committer**. Il faut au moins 4 segments après le domaine. La branche de l'adresse n'est **pas** transmise à l'API (c'est la branche par défaut du dépôt qui est interrogée).
- Gist : le 2e segment du chemin est l'identifiant du Gist. L'app lit la date de **dernière mise à jour du Gist entier** (`updated_at`).
- GitLab : l'adresse est lue comme `propriétaire / dépôt / (segment ignoré) / référence / chemin`. L'app demande les commits du chemin sur cette référence et prend la date du premier commit renvoyé (`committed_date`). Ce découpage correspond à l'ancien format `.../dépôt/raw/branche/fichier`.
- Si l'adresse ne peut pas être découpée, si l'API ne répond pas, répond en erreur, ou renvoie une liste vide ou une date illisible : la date en ligne est considérée **inconnue** (pas d'erreur visible).

### 3.4 Liste perso (source interne)
- Toujours présente, toujours active, invisible dans l'écran des sources, jamais téléchargée ni vidée par une synchronisation.
- Elle seule peut contenir à la fois des entrées bloquées, autorisées et redirigées.

### 3.5 Adresse non reconnue
- Une adresse qui passe la validation mais ne commence pas exactement par `https://` ou `content://` (par exemple `HTTPS://...` en majuscules) est enregistrée mais **jamais lue** : elle est sautée en silence et comptée comme réussie. À ne pas reproduire.

## 4. Écran "Sources"

Titre EN "Hosts sources" / FR "Sources de fichiers hosts". Accès depuis la carte "sources" de l'accueil.

### 4.1 Liste
- Toutes les sources sauf la liste perso, triées par libellé (ordre binaire : majuscules avant minuscules).
- Chaque carte montre : une case "activée", le libellé, l'adresse complète, une ligne de statut, un compteur d'hôtes.
- Toucher la carte ouvre la modification. Le bouton flottant "+" ouvre l'ajout.

### 4.2 Ligne de statut d'une carte
Calculée à partir de deux dates par source : date "en ligne" (dernière modification connue côté serveur) et date "locale" (dernière installation réussie).

| Situation | EN | FR |
|---|---|---|
| Source désactivée | Disabled | Désactivée |
| Date en ligne connue, jamais installée | Last update %1$s ago | Dernière mise à jour il y a %1$s |
| Date en ligne plus récente que l'installation | Need update for %1$s | Mise à jour proposée depuis %1$s |
| Installée après la date en ligne | Up-to-date for %1$s | À jour depuis %1$s |
| Date en ligne inconnue, déjà installée | Installed %1$s ago | Installée il y a %1$s |
| Aucune date | Unknown update status | L'état de mise à jour est inconnu |

`%1$s` est une durée approximative calculée depuis la date en ligne (ou la date locale dans le cas "Installed") jusqu'à maintenant :
- moins de 60 minutes (ou date dans le futur) : "few minutes" / "quelques minutes" ;
- moins de 24 h : "%d hour(s)" / "%d heure(s)" ;
- moins de 30 jours : "%d day(s)" / "%d jour(s)" ;
- sinon : "%d month(s)" / "%d mois", avec des mois de 30 jours.

### 4.3 Compteur d'hôtes d'une carte
- Nombre de lignes retenues pour cette source lors de sa dernière lecture (les doublons internes à la source sont comptés).
- Masqué si la source est désactivée ou si le nombre vaut 0.
- Format "%s hosts" (EN) avec abréviation k / M. L'arrondi actuel est incohérent (voir défauts). Spécification attendue : affichage compact localisé (ex. 950, 1,2 k, 85 k, 1,3 M).

### 4.4 Activer / désactiver une source
- Cocher ou décocher la case bascule la source immédiatement.
- Effet immédiat : toutes les entrées déjà lues de cette source sont marquées actives ou inactives. Les compteurs de l'accueil changent tout de suite.
- La liste finale n'est **pas** recalculée tant que l'utilisateur n'a pas appliqué ou synchronisé.
- À la synchronisation suivante, une source désactivée est **vidée** : ses entrées sont supprimées et ses dates, son ETag et son compteur sont remis à zéro. Réactivée plus tard, elle sera retéléchargée en entier.
- Une modification déclenche le bandeau "Appliquer" (section 11).

### 4.5 Formulaire d'ajout / modification
Titre : "Add source" / "Ajouter une source", ou "Edit source" / "Modifier une source".

Champs, dans l'ordre :
1. **Libellé** ("Label" / "Étiquette"). Obligatoire.
2. **Format de liste** ("List format" / "Format des listes") : choix exclusif "Block" / "Bloquer" (par défaut) ou "Allow" / "Autoriser".
3. **Type** : choix exclusif "URL" (par défaut) ou "File" / "Fichier". Un des deux est toujours sélectionné.
   - En URL : champ "Location" / "Emplacement", prérempli avec `https://`.
   - Passer sur "Fichier" vide le champ URL et ouvre aussitôt le sélecteur de documents. Le texte "Press File to select source file" / "Touchez Fichier pour sélectionner le fichier source" s'affiche à la place, puis l'identifiant du fichier choisi. Toucher ce texte rouvre le sélecteur.
   - Revenir sur "URL" remet `https://` dans le champ.
4. **Case "Apply redirected hosts" / "Appliquer les hôtes redirigés"** avec l'avertissement :
   - EN : "Allowing redirected hosts may cause security issues. Only use this settings on a trusted source as it could redirect some sensitive traffic to whatever server it wants."
   - FR : "Autoriser les hôtes redirigés peut entraîner des problèmes de sécurité. N'utilisez ces paramètres que sur une source de confiance, car du trafic sensible pourrait être redirigé à votre insu vers n'importe quel serveur."
   - Visible seulement quand le format "Bloquer" est choisi. En format "Autoriser", l'option est forcée à "non" à l'enregistrement.

Actions : "Enregistrer" (icône dans la barre) et, en modification seulement, "Supprimer".

Validations, dans l'ordre, au moment d'enregistrer (le message s'affiche sous le champ fautif) :
- Libellé vide : "Label required" / "Une étiquette est nécessaire". (Un libellé fait uniquement d'espaces est accepté, à corriger.)
- URL vide : "Location required" / "Un emplacement est nécessaire".
- URL invalide : "Invalid location" / "L'emplacement est invalide". Est valide : une adresse qui commence par `https://` (comparaison insensible à la casse) et qui n'est pas exactement `https://`, ou une adresse `content://`. Aucune autre vérification (domaine, espaces) n'est faite.
- Fichier non choisi : même message "Invalid location".

Enregistrement :
- Ajout : la nouvelle source est créée **activée**, sans date, ETag ni compteur.
- Modification : l'ancienne source est **supprimée** puis une nouvelle est créée avec les valeurs du formulaire. Conséquences actuelles (à ne pas reproduire) : l'état activé/désactivé est perdu (repasse à activée), les dates et l'ETag sont perdus, les entrées sont effacées, et la priorité entre sources change (voir 9.2).
- Deux sources ne peuvent pas avoir la même adresse. Actuellement, ajouter une adresse déjà présente ne fait **rien et n'affiche rien** ; en modification, cela supprime la source éditée sans la recréer. À ne pas reproduire.
- Suppression : immédiate, **sans confirmation**, et supprime toutes les entrées de la source. La liste finale n'est recalculée qu'au prochain "Appliquer" ou "Synchroniser".

## 5. Vérification de mise à jour (sans téléchargement)

Déclencheurs : au démarrage de l'accueil (si l'option est active, section 10) et par la tâche périodique.

Déroulé :
1. Si l'appareil n'a aucun réseau actif offrant Internet : erreur "pas de connexion". Le simple fait d'être connecté suffit, la validation du réseau par Android n'est pas exigée.
2. Seules les sources **activées** sont vérifiées (hors liste perso). Aucune source : "pas de mise à jour".
3. Pour chaque source, l'app obtient une date en ligne :
   - Git (section 3.3) : date de l'API.
   - Autre URL : requête HTTP **HEAD** avec les en-têtes conditionnels connus (`If-None-Match` avec l'ETag enregistré, `If-Modified-Since` avec la date en ligne enregistrée). Elle lit l'en-tête `Last-Modified` (format date HTTP standard). Si cet en-tête manque et que la réponse est 304, la date en ligne enregistrée est conservée. Sinon, ou en cas d'erreur réseau ou de date illisible, la date est inconnue.
   - Fichier local : date de dernière modification fournie par le fournisseur du document, si elle existe.
4. La date en ligne obtenue est **enregistrée**, même si elle est inconnue (elle efface alors la précédente, à ne pas reproduire).
5. Une mise à jour est disponible pour la source si :
   - date en ligne connue : jamais installée, ou date en ligne postérieure à la date locale ;
   - date en ligne inconnue : installée il y a **plus d'une semaine**.
6. Statuts affichés pendant l'opération (ligne d'état de l'accueil) : "Checking for update" / "Vérification de la présence de mises à jour", puis "Checking %s source for update" / "Vérification de la présence de mises à jour sur la source %s" (libellé), puis "Source update available" / "Une mise à jour des sources est proposée" ou "No source update found" / "Aucune mise à jour des sources n'a été trouvée".

## 6. Synchronisation (téléchargement et lecture)

Déclencheurs : bouton de synchronisation de la carte sources de l'accueil, bandeau "Appliquer" de l'écran sources, assistant d'accueil, tâche périodique (si l'application automatique est active), bouton "Réessayer" des dialogues d'erreur.

### 6.1 Déroulé
1. Pas de réseau Internet : erreur "pas de connexion", **même si toutes les sources sont des fichiers locaux** (à ne pas reproduire).
2. Statut "Retrieving sources" / "Récupération des sources".
3. Pour chaque source (hors liste perso), triées par libellé :
   - **Désactivée** : vidée (entrées supprimées, dates, ETag et compteur remis à zéro), puis on passe à la suivante.
   - Sinon, l'app redemande la date en ligne (même méthode qu'en section 5). Inconnue : on prend "maintenant".
   - Si la date locale existe et est **strictement postérieure** à la date en ligne : source sautée ("à jour").
   - Sinon la source est lue :
     - URL : statut "Downloading source %s" / "Téléchargement de la source %s" (avec l'adresse). Requête GET avec `If-None-Match` (ETag enregistré) et `If-Modified-Since` (date en ligne enregistrée). Réponse 304 : contenu inchangé, les entrées existantes sont gardées. Sinon, l'ETag reçu est enregistré et le contenu est lu.
     - Fichier : statut "Reading source %s" / "Lecture de la source %s" (avec l'adresse), puis lecture du fichier.
     - Lecture du contenu : statut "Parsing %s source" / "Analyse de la source %s" (avec le libellé). Les anciennes entrées de la source sont supprimées, puis les nouvelles insérées (règles en section 7).
   - Réussite : date locale = la plus tardive entre "maintenant" et la date en ligne ; date en ligne enregistrée ; compteur de la source recalculé.
4. Si **toutes** les sources tentées ont échoué (et au moins une tentée) : erreur "téléchargement impossible", la liste finale n'est pas recalculée.
5. Sinon (y compris en cas d'échec partiel, sans rien signaler) : statut "Syncing rule database" / "Synchronisation de la base de données des règles", puis recalcul de la liste finale (section 9). L'indicateur "mise à jour disponible" repasse à faux.

Conséquence des dates : une source dont le serveur ne donne aucune date est retéléchargée à chaque synchronisation (la requête conditionnelle évite alors de relire un contenu identique si le serveur gère ETag ou If-Modified-Since).

### 6.2 Réseau, cache, délais
- Un cache HTTP local de 100 Mo est utilisé pour les téléchargements.
- Délais réseau : ceux par défaut de la bibliothèque HTTP (environ 10 s pour la connexion, 10 s d'inactivité en lecture), non configurables.
- Délai maximal de lecture et d'insertion d'une source : **30 minutes**. Au-delà, la source est en échec.
- Une lecture coupée en cours de route (connexion réinitialisée, lecture interrompue, erreur d'écriture) met la source **en échec** : ses dates ne sont pas mises à jour, elle sera retentée. Mais ses anciennes entrées ont déjà été effacées et les entrées partielles restent (à ne pas reproduire, voir défauts).
- Le code de réponse HTTP n'est pas vérifié : une page d'erreur 404 ou 500 est lue comme une liste (voir défauts).
- Aucune limite de taille de fichier. La lecture se fait en flux, sans charger tout le fichier en mémoire.
- Encodage : celui annoncé par le serveur, UTF-8 par défaut ; UTF-8 pour les fichiers locaux. Les fins de ligne `\n` et `\r\n` sont acceptées.

### 6.3 Erreurs visibles (dialogue sur l'accueil)
| Cas | Titre EN / FR | Détail EN / FR |
|---|---|---|
| Pas de réseau | No Internet connection / Aucune connexion à Internet | Unable to establish a connection to the Internet. Please check your device connectivity. / Impossible d'établir une connexion vers Internet. Vérifiez la connectivité de votre appareil. |
| Toutes les sources en échec | Failed to download hosts source / Échec de téléchargement de la source du fichier hosts | None of your enabled hosts sources are reachable. Please check you are properly connected to the Internet. / Aucune de vos sources de fichiers hosts activées n'est accessible. Assurez-vous que votre appareil est bien connecté à Internet ? |

Le dialogue ajoute un texte d'aide générique et deux boutons : "Help" / aide, et pour ces deux erreurs "Retry sync" (relance une synchronisation). Pour les autres erreurs, "Close".

Une seule opération (vérification, synchronisation, activation/pause) peut tourner à la fois depuis l'accueil : un nouvel appui pendant une opération est ignoré. Une barre de progression et la ligne d'état sont affichées pendant l'opération.

## 7. Formats acceptés et règles de lecture

Règles communes à chaque ligne :
- Une ligne vide est ignorée.
- Une ligne dont le **premier caractère** est `#` est un commentaire, ignorée.
- Aucune limite de longueur de ligne.

### 7.1 Source au format "Bloquer" (format fichier hosts)
Une ligne est retenue seulement si elle a la forme : espaces éventuels, **jeton 1**, au moins un espace ou une tabulation, **jeton 2**, puis n'importe quoi. Un jeton ne contient ni espace ni `#`. Tout ce qui suit le jeton 2 (autres noms, commentaire) est ignoré.

- Jeton 2 = nom d'hôte. S'il vaut exactement `localhost` (minuscules), la ligne est ignorée.
- Jeton 1 = adresse IP :
  - exactement `127.0.0.1`, `0.0.0.0` ou `::1` : entrée **bloquée** ;
  - toute autre valeur : si l'option "redirections" de la source est active, entrée **redirigée** vers cette IP, à condition que ce soit une IPv4 ou IPv6 valide ; sinon ligne ignorée.
- Nom d'hôte d'une entrée bloquée : refusé s'il contient `*` ou `?`, puis doit être un nom valide (7.3).
- Nom d'hôte d'une entrée redirigée : doit être un nom valide **avec jokers tolérés** (7.4). (Incohérence : un joker est accepté ici mais ne sert à rien.)
- Les noms ne sont **pas** mis en minuscules : ils sont gardés tels quels.

Exemples (option redirections désactivée, sauf mention) :

| Ligne | Résultat |
|---|---|
| `127.0.0.1 ads.example.com` | bloqué `ads.example.com` |
| `0.0.0.0 ads.example.com # pub` | bloqué |
| `::1 ads.example.com` | bloqué |
| `  0.0.0.0<TAB>ads.example.com` | bloqué |
| `0.0.0.0 ads.example.com#x` | bloqué `ads.example.com` |
| `0.0.0.0 a.com b.com` | bloqué `a.com` seulement |
| `ads.example.com` (domaine seul) | **ignoré** |
| `\|\|ads.example.com^` (style Adblock) | ignoré |
| `0.0.0.0 localhost` | ignoré |
| `127.0.0.1 localhost.localdomain` | **bloqué** (seul `localhost` exact est filtré) |
| `::1 ip6-localhost` | **bloqué** |
| `0.0.0.0 0.0.0.0` | ignoré (nom invalide) |
| `0.0.0.0 *.example.com` | ignoré |
| `0.0.0.0 Ads.Example.COM` | bloqué, casse conservée |
| `127.0.0.2 x.com` | ignoré ; redirigé vers 127.0.0.2 si l'option est active |
| `192.168.1.10 nas.lan` | ignoré ; redirigé si l'option est active |
| `255.255.255.255 broadcasthost` | ignoré ; redirigé si l'option est active |
| `0:0:0:0:0:0:0:0 x.com` | ignoré ; redirigé vers cette IP si l'option est active (forme longue non reconnue comme blocage) |
| `# 0.0.0.0 x.com` | commentaire |
| `   # 0.0.0.0 x.com` | ignoré (ne correspond pas à la forme attendue) |

**Les listes "un domaine par ligne" ne sont donc pas prises en charge en format Bloquer.**

### 7.2 Source au format "Autoriser"
- Chaque ligne est un nom d'hôte, éventuellement avec jokers.
- Tout ce qui suit le premier `#` est retiré, puis les espaces de début et de fin.
- Le reste doit être un nom valide avec jokers (7.4), sinon la ligne est ignorée.
- Une ligne au format hosts (`0.0.0.0 x.com`) est donc **ignorée** (l'espace la rend invalide).
- Toutes les entrées d'une telle source sont de type "autorisée". L'option redirections n'existe pas pour ce format.

### 7.3 Nom d'hôte valide (sans joker)
D'après les tests de la référence :
- longueur totale au plus 253 caractères, au plus 127 niveaux ;
- chaque niveau : 1 à 63 caractères, lettres, chiffres, `-` ou `_`, sans commencer ni finir par `-` ou `_` ;
- le dernier niveau ne commence pas par un chiffre ;
- un seul niveau est accepté (`www`, `x`) ;
- une adresse IP n'est pas un nom valide ;
- un point final unique semble accepté (`example.com.`) et le nom est alors stocké avec ce point (à confirmer, voir questions) ;
- les lettres non ASCII semblent acceptées sans conversion (à confirmer).

Valides : `foo.com`, `f-_-o.cOM`, `f--1.com`, `f11-1.com`, `www`, `abc.a23`, `biz.com.ua`, `x`, `fOo`, `f--o`, `f_a`.
Invalides : vide, espace, `127.0.0.1`, `::1`, `13`, `abc.12c`, `foo-.com`, `_bar.quux`, `foo+bar.com`, `foo!bar.com`, `.foo.com`, `..bar.com`, `baz..com`, `fleeb.com..`, `.`, `..`, `...`, niveau de 64 caractères, 128 niveaux, plus de 253 caractères.

### 7.4 Nom avec jokers (entrées autorisées)
- `*` remplace n'importe quelle suite de caractères (y compris vide et y compris des points).
- `?` remplace exactement un caractère.
- Validation : le motif est accepté s'il reste un nom valide **soit** en retirant tous les jokers, **soit** en remplaçant chaque joker par la lettre `a`. On ne rejette que les motifs qui ne peuvent jamais correspondre à un nom valide.

Valides : `*.example.com`, `?.example.com`, `*-example.com`, `*_example.com`, `example.*`, `example.?`, `example-*`, `example_?`, `sub.*.example.com`, `f--?.com`, `*`, `?`.
Invalides : `abc.12*`, `foo*-.com`, `_*bar.quux`, `foo?+*bar.com`, `.*foo.com`, `..*bar.com`, `*..bar.com`, `baz*..com`, `fleeb.com*..`, `fleeb.com..*` (et leurs variantes avec `?`).

### 7.5 Adresse IP valide (redirections)
IPv4 en notation pointée complète (4 nombres) ou IPv6 (formes longues et abrégées). Toute autre chaîne est refusée.

## 8. Listes perso (bloqués, autorisés, redirigés)

Écran "Your lists" / "Vos listes", trois onglets en bas : "Blocked" / "Bloqués", "Allowed" / "Autorisés", "Redirected" / "Redirigés". Ouvert depuis les compteurs de l'accueil, sur l'onglet correspondant.

### 8.1 Affichage
- Chaque onglet liste les entrées de ce type, triées par nom, **un nom affiché une seule fois** même s'il vient de plusieurs sources.
- Par défaut l'écran mélange liste perso et sources. Le menu "Toggle sources" / "Activer/désactiver les sources" bascule vers la liste perso seule, et inversement.
- Recherche ("Filter hosts" / "Filtrer les hôtes", indice "Search hostname…" / "Chercher un nom d'hôte…") : correspondance "contient", insensible à la casse, `*` et `?` utilisables comme jokers. Le bouton retour efface la recherche.
- L'onglet Redirigés affiche le nom et, en dessous, l'IP cible.
- Entrée de la liste perso : case cochable (active / inactive) ; appui long : mode édition avec "Edit" / "Modifier" et "Delete" / "Supprimer".
- Entrée venant d'une source : case grisée non modifiable ; appui long : copie du nom dans le presse-papiers avec le message "Host copied to clipboard" / "L'hôte a été copié dans le presse-papiers".
- Bouton flottant "Add host" / "Ajouter un hôte" : ouvre la saisie pour l'onglet courant.

### 8.2 Saisie et validation
Le bouton de validation reste grisé tant que la saisie est invalide (contrôle à chaque frappe). La saisie n'est ni nettoyée (espaces) ni mise en minuscules.

| Onglet | Titre ajout EN / FR | Champs | Règle |
|---|---|---|---|
| Bloqués | Add host to blacklist / Ajouter un hôte à la liste noire | Nom d'hôte | nom valide, **sans joker** |
| Autorisés | Add host to whitelist / Ajouter un hôte à la liste blanche | Nom d'hôte, mention "(Wildcard characters * and ? are allowed)" / "(Les métacaractères * et ? sont permis)" | nom valide **avec jokers** |
| Redirigés | Add host redirect / Ajouter une redirection d'hôte | Nom d'hôte, "IP (IPv4 or IPv6):" / "IP (IPv4 ou IPv6) :" | nom valide sans joker **et** IP valide |

Titres de modification : "Edit blacklisted host" / "Modifier l'hôte mis en liste noire", "Edit whitelisted host" / "Modifier l'hôte mis en liste blanche", "Edit redirect" / "Modifier la redirection". Boutons "Add"/"Save" et "Cancel".

### 8.3 Règles de la liste perso
- Un nom n'existe qu'**une fois** dans la liste perso, tous types confondus. Ajouter un nom déjà présent (même dans un autre onglet) **remplace** l'entrée existante : elle change de type, prend la nouvelle IP et redevient active. Aucun message ne le signale.
- La modification change le nom et/ou l'IP, garde l'état actif, mais ne vérifie pas les doublons (deux entrées perso peuvent alors porter le même nom).
- Suppression immédiate, sans confirmation ni annulation.
- Les entrées perso inactives sont ignorées dans le calcul.
- Toute modification affiche le bandeau "Appliquer" de cet écran (section 11, sans téléchargement).

## 9. Calcul de la liste finale

### 9.1 Étapes
Le calcul part de zéro à chaque fois :
1. On prend tous les noms **bloqués actifs** : sources activées et liste perso.
2. On retire chaque nom qui correspond à **une entrée autorisée active** (liste perso ou source au format Autoriser), en correspondance exacte ou par jokers, **insensible à la casse**.
3. On ajoute toutes les **redirections actives** (liste perso ou sources avec redirections). Une redirection remplace un blocage existant pour le même nom, et n'est **pas** annulée par une entrée autorisée.

Les doublons de noms sont fusionnés.

### 9.2 Priorités
- Redirigé > Autorisé > Bloqué.
- Plusieurs redirections pour un même nom : celle de la liste perso gagne ; entre sources, gagne la source créée le plus tôt (une source modifiée est recréée, donc perd sa priorité).
- Il n'y a pas de priorité entre sources pour le blocage ou l'autorisation : un nom autorisé par n'importe quelle source active (y compris une source tierce au format Autoriser) est débloqué, même s'il est bloqué dans la liste perso.

### 9.3 Sous-domaines
- Le blocage est **exact** : bloquer `example.com` ne bloque pas `ads.example.com`.
- L'autorisation est exacte sauf jokers : autoriser `example.com` ne débloque pas `ads.example.com` ; `*.example.com` débloque les sous-domaines mais pas `example.com` ; `*example.com` débloque les deux, mais aussi `badexample.com`.
- La redirection est exacte.

### 9.4 Utilisation par les modes de blocage (résumé, détails dans les zones root et VPN)
- Root : le fichier hosts écrit contient un en-tête (date de génération, avertissement, liste des sources actives au format "libellé:adresse", hors liste perso), les lignes `127.0.0.1 localhost` et `::1 localhost`, puis pour chaque nom bloqué une ligne avec l'IPv4 de redirection (réglage, défaut `127.0.0.1`), plus une ligne IPv6 (réglage, défaut `::1`) si "Enable IPv6 support" est actif (défaut : non), et pour chaque redirection une ligne "IP nom".
- VPN : chaque requête DNS est mise en minuscules puis cherchée **exactement** dans la liste finale. Un nom stocké avec des majuscules ne correspond donc jamais (voir défauts).

## 10. Mise à jour automatique en arrière-plan

Réglages (écran "Updates" / "Mises à jour", catégorie "Hosts updates" / "Mises à jour des hôtes") :

| Réglage EN / FR | Défaut | Effet |
|---|---|---|
| Check for update at startup / Vérifier la présence d'une mise à jour au démarrage | activé | à l'ouverture de l'accueil (téléphone uniquement, pas sur TV), lance une **vérification** (section 5), sans téléchargement |
| Periodically check for update / Vérifier régulièrement la présence de mises à jour | activé | planifie la tâche périodique |
| Sync on update / Synchroniser lors d'une mise à jour | activé | dépend du précédent ; si une mise à jour est trouvée, synchronise et applique automatiquement |
| Sync on unmetered network only / Ne synchroniser que sur les réseaux sans limite de données | désactivé | dépend du précédent ; la tâche n'a lieu que sur réseau non limité |

Tâche périodique :
- Toutes les **6 heures**, première exécution **3 heures** après la planification.
- Conditions : réseau connecté (ou non limité selon le réglage) et stockage non saturé.
- Planifiée au démarrage de l'app si le réglage est actif, sans réinitialiser une planification existante. Activer le réglage ou changer "réseau non limité" replanifie. Désactiver annule.
- Déroulé : vérification (section 5). En cas d'erreur (pas de réseau) : nouvel essai plus tard selon la politique de réessai du système. Si une mise à jour est trouvée :
  - "Sync on update" actif : synchronisation complète puis application **seulement si le blocage est actif** (VPN démarré par l'utilisateur, ou fichier hosts actuellement géré par l'app en root). Jamais de redémarrage d'un blocage arrêté par l'utilisateur. En cas d'échec : abandon silencieux de cette exécution.
  - sinon : notification "Source update available" / "Une mise à jour des sources est proposée", texte "Newer hosts files available for download." / "Des fichiers hosts plus récents peuvent être téléchargés.", canal "Hosts updates" / "Mises à jour des hôtes", priorité basse, se ferme au toucher et ouvre l'accueil. Pas de notification si les notifications de l'app sont refusées.
- Les notifications de mise à jour sont effacées à l'ouverture de l'accueil.

## 11. Flux "Appliquer" et ce qui le déclenche

### 11.1 Bandeau d'application
Texte "Your configuration changed. You need to apply it." / "Votre configuration a changé. Vous devez l'appliquer.", action "Apply" / "Appliquer". Pendant l'opération : "Applying new configuration…" / "Application de la nouvelle configuration…" avec une roue. En cas d'échec : "Failed to apply new configuration." / "Échec d'application de la nouvelle configuration." Le premier état à l'ouverture de l'écran ne déclenche jamais le bandeau.

| Écran | Déclencheurs | Ce que fait "Appliquer" |
|---|---|---|
| Sources | activer/désactiver, ajouter, modifier, supprimer une source | **synchronisation complète** (section 6), puis application si le blocage est actif |
| Listes perso | ajouter, modifier, supprimer, activer/désactiver une entrée perso | **recalcul seul** de la liste finale (pas de téléchargement), puis application si le blocage est actif |

Sur TV, le bandeau est remplacé par un bouton "Appliquer" dans l'en-tête, avec le même comportement.

### 11.2 Autres chemins
- Bouton de synchronisation de l'accueil : synchronisation complète puis application si le blocage est actif.
- Activation depuis l'assistant d'accueil (ou première activation VPN sur TV) : synchronisation complète puis application **forcée** (démarre le blocage).
- Bouton principal pause/reprise de l'accueil : applique ou retire la liste finale **déjà calculée**, sans synchroniser.

### 11.3 Règle générale
"Appliquer" depuis un écran ou une tâche automatique ne réactive jamais un blocage que l'utilisateur a arrêté : les données sont mises à jour, mais le blocage reste éteint, sans message.

## 12. Statistiques affichées

Accueil :
- **Bloqués** ("Blocked" / "Bloqués", sous-titre "Ads & trackers" / "Pubs & traqueurs") : nombre de noms distincts de type bloqué, actifs, toutes sources et liste perso confondues. Ce n'est **pas** la taille de la liste finale (les noms autorisés ou redirigés y restent comptés).
- **Autorisés** ("Allowed" / "Autorisés", "Exceptions") : nombre d'entrées autorisées distinctes actives (un motif à joker compte pour 1).
- **Redirigés** ("Redirected" / "Redirigés", "Custom rules" / "Règles personnalisées") : nombre de noms redirigés distincts actifs.
- Ces trois compteurs se mettent à jour en direct (y compris dès qu'on coche ou décoche une source).
- Carte sources : "%d up-to-date source(s)" / "%d source(s) à jour" = sources activées dont la date en ligne est antérieure ou égale à la date locale ; "%d outdated source(s)" / "%d source(s) périmée(s)" = sources activées dont la date en ligne est postérieure. Les sources sans date (jamais lues, ou serveur sans date) ne sont comptées nulle part.
- Écran sources : compteur par source (section 4.3).
- L'indicateur interne "mise à jour disponible" est calculé mais n'est affiché nulle part directement.

## 13. Défauts ou bizarreries constatés (à NE PAS reproduire)

1. **Code HTTP non vérifié** : une réponse 404, 403 ou 500 est lue comme une liste. La source est vidée puis marquée "à jour". Il faut traiter tout code autre que 200 et 304 comme un échec et garder les anciennes données.
2. **Remplacement non atomique** : les anciennes entrées d'une source sont effacées avant la lecture. Si la lecture échoue en route, la source reste partiellement remplie et la liste finale est quand même recalculée avec ces restes. Il faut ne remplacer les entrées qu'une fois la lecture complète réussie.
3. **Échec partiel silencieux** : si certaines sources échouent et d'autres réussissent, rien n'est signalé. Il faut indiquer quelles sources ont échoué.
4. **Modification = suppression puis recréation** : perte de l'état activé, des dates, de l'ETag, des entrées et de la priorité. Il faut modifier la source en place et ne retélécharger que si l'adresse ou le format change.
5. **Adresse en double** : l'ajout est ignoré sans message, et en modification la source éditée disparaît. Il faut refuser avec un message clair.
6. **Suppression sans confirmation**, pour les sources comme pour les entrées perso, et sans annulation.
7. **Casse non normalisée** : les noms sont stockés tels quels. En VPN, les requêtes sont mises en minuscules, donc une entrée `Ads.Example.com` ne bloque rien ; les doublons ne diffèrent que par la casse. Il faut tout mettre en minuscules à la lecture et à la saisie.
8. **`_` littéral traité comme joker** à l'étape d'autorisation : `f_a` autorisé débloque aussi `fxa`. Le moteur doit traiter `_` comme un caractère normal.
9. **Pseudo-localhost bloqués** : seul `localhost` exact est filtré ; `localhost.localdomain`, `local`, `ip6-localhost`, `ip6-loopback` (présents en tête de StevenBlack) deviennent des entrées bloquées. À filtrer.
10. **Formes d'adresse de blocage incomplètes** : `0`, `0:0:0:0:0:0:0:0`, `::`, `127.0.0.x` ne sont pas reconnues comme blocage.
11. **Listes "domaine seul" ignorées** en format Bloquer, alors que c'est un format très répandu.
12. **Erreur "pas de connexion" pour des fichiers locaux** : la synchronisation exige Internet même sans source distante.
13. **Date en ligne effacée** lors d'une vérification qui échoue, ce qui fait disparaître l'information "à jour depuis" et casse l'en-tête conditionnel `If-Modified-Since` suivant.
14. **ETag faible modifié** : le préfixe `W/` est retiré avant d'être renvoyé. Il faut renvoyer l'ETag exactement comme reçu.
15. **Double requête par source** (HEAD puis GET) lors d'une synchronisation : une seule requête conditionnelle suffit.
16. **API GitHub** : la branche de l'adresse est ignorée, et les adresses `refs/heads/...` sont mal découpées. **API GitLab** : le format actuel `/-/raw/` est mal découpé (la référence lue est "raw"), les sous-groupes ne sont pas gérés. **Gist** : la date est celle du Gist entier, pas du fichier. Pas de gestion de la limite d'appels anonymes de GitHub.
17. **Détection Git et type d'adresse sensibles à la casse** alors que la validation ne l'est pas : `HTTPS://...` est acceptée puis jamais lue, et comptée comme réussie.
18. **Validation d'URL trop faible** : aucun contrôle du domaine ni des espaces ; libellé fait d'espaces accepté.
19. **Saisie perso** : pas de suppression automatique des espaces, pas de minuscules ; doublons possibles après modification ; un ajout depuis un autre onglet remplace en silence une entrée existante ; le journal DNS ajoute, lui, sans vérifier les doublons (comportement incohérent).
20. **Affichage groupé par nom** dans les listes : si un nom est à la fois dans la liste perso et dans une source, la ligne affichée peut être celle de la source, ce qui empêche de modifier ou supprimer l'entrée perso.
21. **Compteur "Bloqués" trompeur** : il inclut les noms ensuite autorisés ou redirigés.
22. **Compteur par source** : compte les doublons internes ; arrondi incohérent (1000 s'affiche "1000", 1500 s'affiche "2k", 9999 s'affiche "10k").
23. **Bandeau "Appliquer" de l'écran sources (téléphone)** : il réagit à toute écriture dans les sources, y compris une synchronisation en arrière-plan, et ne réagit pas à la suppression de la dernière source (liste vide ignorée).
24. **"Appliquer" depuis l'écran sources lance un téléchargement complet**, même pour une simple désactivation, et échoue hors ligne.
25. **"Appliquer" quand le blocage est arrêté** : ne fait rien côté blocage et n'en informe pas l'utilisateur.
26. **Assistant d'accueil** : réactive toutes les sources désactivées sans demander.
27. **Redirection avec joker** acceptée depuis une source alors qu'elle ne peut pas fonctionner.
28. **Autorisation d'une source tierce prioritaire sur un blocage perso**, et redirection d'une source prioritaire sur une autorisation perso : la volonté explicite de l'utilisateur devrait primer.
29. **Libellés par défaut figés** dans la langue de l'installation.
30. **Textes erronés** : la traduction FR de "%s hosts" est "Fichier(s) hosts de %s" (devrait être "%s hôtes") ; l'indice du champ nom d'hôte des listes dit "The hosts source URL" / "L'URL de la source du fichier hosts" ; un ancien texte mentionne `file://`, non accepté ; "Pete Lowe blocklist hosts" est traduit "Fichier hosts unifié de Pete Lowe".
31. **Tâche dite "quotidienne"** dans les réglages internes alors qu'elle tourne toutes les 6 heures.
32. **Pas de limite de taille** ni de nombre d'entrées par source : un serveur hostile peut envoyer un fichier énorme (seul garde-fou : 30 minutes).
33. **Caractère BOM UTF-8** en début de fichier non retiré : la première ligne peut être mal lue.
34. **Ancienne permission de fichier** jamais libérée quand on choisit un autre fichier ou supprime la source.

## 14. Questions ouvertes

1. Faut-il garder les trois sources par défaut toutes activées, ou seulement la liste AdAway (plus légère) ?
2. Prendre en charge les listes "un domaine par ligne" en format Bloquer ? Et le format Adblock simple (`||domaine^`) ?
3. Le blocage doit-il couvrir les sous-domaines (bloquer `example.com` bloque `*.example.com`) ? Si oui, uniquement pour la liste perso, ou pour tout ?
4. Ordre de priorité souhaité entre liste perso et sources : la liste perso doit-elle toujours gagner (autorisation perso > redirection de source, blocage perso > autorisation de source) ?
5. Faut-il des jokers pour les entrées bloquées et redirigées de la liste perso ?
6. Que faire en cas d'échec partiel de synchronisation : appliquer quand même avec les anciennes données des sources en échec (recommandé), ou tout annuler ?
7. Faut-il une limite de taille par source (par exemple 50 Mo ou 2 millions d'entrées) et un message dédié ?
8. Les adresses HTTP en clair doivent-elles rester interdites ?
9. Garder la récupération de date via les API Git, ou s'appuyer uniquement sur ETag et Last-Modified (plus simple, sans limite d'appels) ?
10. Le point final (`example.com.`) et les noms non ASCII : normaliser (retirer le point, convertir en punycode) ou refuser ?
11. "Appliquer" depuis l'écran sources : recalcul seul pour une activation/désactivation/suppression, téléchargement seulement pour une source nouvelle ou modifiée ?
12. Que montrer quand l'utilisateur applique alors que le blocage est arrêté : un message, ou proposer de l'activer ?
13. Fréquence de la tâche périodique (6 h actuellement) : la rendre réglable ?
14. Faut-il une vérification au démarrage aussi sur TV ?
15. Le compteur "Bloqués" doit-il refléter la liste finale réelle ?
16. L'assistant d'accueil doit-il continuer à réactiver toutes les sources ?
17. Faut-il afficher l'erreur par source (dernier échec, code HTTP) sur la carte de la source ?

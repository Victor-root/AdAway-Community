# 03. Mode root

## Périmètre et constat de départ

Le mode root bloque les publicités en remplaçant le fichier système `/system/etc/hosts` par un fichier généré par l'app. Il comprend aussi deux outils qui tournent avec les droits root : un serveur web local (qui répond aux requêtes vers les domaines bloqués) et une capture des requêtes DNS (journal DNS).

Constat important sur la référence actuelle :
- La cible d'écriture est **toujours et uniquement** `/system/etc/hosts`. Il n'existe plus de choix de cible, plus de chemin personnalisé, plus de création de lien symbolique, plus de bascule « systemless » pilotée par l'app, plus de proposition de redémarrage.
- Des textes de ces anciennes fonctions restent dans les ressources sans jamais être affichés (liste des cibles `/system/etc/hosts`, `/data/hosts`, `/data/data/hosts`, « Custom target » avec valeur par défaut `/data/etc/hosts`, messages sur les liens symboliques, sur l'activation/désactivation du mode systemless, sur le redémarrage, « Remount failed », « Root access denied »). Ils ne décrivent aucun comportement actuel.
- Le mode « systemless » repose entièrement sur l'utilisateur : il doit activer lui-même le module « Systemless hosts » de Magisk. L'app se contente d'écrire dans `/system/etc/hosts` ; si ce fichier est recouvert par le module, l'écriture atterrit dans le fichier du module. Les messages d'erreur et l'aide renvoient vers ce module.

## Détection du root et demande des droits

### Au premier lancement (écran d'accueil, choix du mode)
- L'utilisateur touche la carte « Root based ad blocking » (« Bloqueur de publicités fondé sur la racine »). Points affichés sur la carte : « Faster », « Battery friendly », « Root required » (FR : « Plus rapide », « Demande en énergie moins élevée », « Exige un appareil débridé »).
- Le choix du mode VPN éventuellement fait avant est annulé (mode « non défini ») et le bouton « suivant » est bloqué.
- La carte devient non cliquable pendant la demande, puis l'app demande un shell root. C'est à ce moment que le gestionnaire de root (Magisk, KernelSU, etc.) affiche sa fenêtre d'autorisation.
- Si le root est accordé : le mode root est enregistré, la carte root est mise en surbrillance, la carte VPN revient à la couleur normale, « suivant » est autorisé.
- Si le root est refusé, absent, ou si la réponse est indéterminée : le mode redevient « non défini », « suivant » reste bloqué et une boîte de dialogue s'affiche :
  - Titre EN « Rooted Android required » / FR « Un Android débridé est exigé ».
  - Texte EN « Either the su binary could not be found or you did not allow root permission for AdAway.\n\nThis can happen when your device is not rooted. You can find information about how to root your device on wiki.lineageos.org or other Android websites. » / FR « Soit le fichier binaire su est introuvable, soit vous n'avez pas donné l'accès racine à AdAway.\n\nCela peut arriver quand votre appareil n'est pas débridé. Vous trouverez des renseignements sur comment débrider votre appareil sur wiki.lineageos.org ou sur d'autres sites Web dédiés à Android. »
  - Un seul bouton « Close » / « Fermer ». L'utilisateur peut réessayer ou choisir le VPN.
- Sur Android TV : bouton « Use root » (« Faster and battery-friendly. Requires a rooted device. »). Même demande de root. Si accordé, l'app passe directement à l'écran d'activation, affiche la progression et lance tout de suite la synchronisation des sources puis l'application du fichier hosts (pas d'écran de consentement intermédiaire). Si refusé, même boîte de dialogue, l'utilisateur reste sur l'écran de choix. En cas d'erreur pendant l'activation, le bouton « réessayer » relance directement synchronisation + application, sans redemander le root.

### Ensuite
- Le root n'est plus vérifié explicitement après l'accueil. Chaque opération root (lecture de l'en-tête du hosts, copie, remontage, serveur web, capture DNS) passe par le shell root. Si le root a été retiré entre-temps, les opérations échouent et l'utilisateur voit l'erreur générique de l'opération (par exemple « Failed to install new hosts file »), jamais un message « root refusé ».
- Au démarrage de l'app en mode root, l'app lit la première ligne du hosts via root : la fenêtre d'autorisation du gestionnaire de root peut donc apparaître dès l'ouverture.
- Le délai d'attente de la demande de root est celui par défaut de la bibliothèque utilisée (non configuré par l'app ; valeur exacte non lisible dans le code de l'app).

## Réglages du mode root

Tous les réglages sont sur l'écran Préférences unique. La section « Root based ad blocker » (« Bloqueur de publicités pour appareil débridé ») est toujours visible mais grisée (tous ses éléments désactivés) quand le mode actif n'est pas root. En mode root, la section est active, et les éléments liés au serveur web ne sont actifs que si le serveur web est activé.

| Ordre | Libellé EN / FR | Type | Défaut | Clé de stockage (utile seulement pour reprendre une installation existante) |
|---|---|---|---|---|
| 1 | « Open hosts file » / « Ouvrir le fichier hosts » | action | | `openHosts` (non stocké) |
| 2 | « Hide reboot dialog after apply » / « Cacher le message de redémarrage après application » | interrupteur | désactivé | `neverReboot` |
| 3 | Texte descriptif « Define where to redirect blocked hosts » / « Définir où rediriger les hôtes bloqués » | texte | | |
| 4 | « Configure IPv4 redirection » / « Redirection IPv4 » | saisie texte | `127.0.0.1` | `redirectionIP` |
| 5 | « Configure IPv6 redirection » / « Redirection IPv6 » | saisie texte | `::1` | `redirectionIPv6` |
| 6 | Texte descriptif du serveur web (voir plus bas) | texte | | |
| 7 | « Enable web server » / « Activer le serveur Web » | interrupteur | désactivé | `webserverEnabled` |
| 8 | « Test web server » / « Tester le serveur Web » | action + état en sous-titre | sous-titre « Checking… » | `webserverTest` (non stocké) |
| 9 | « Install self-signed certificate » / « Installer un certificat autosigné » | action | | `webserverCertificate` (non stocké) |
| 10 | « Replace blank ad space by app icon » / « Remplacer l'espace vide des publicités par l'icône de l'appli » | interrupteur | activé | `webserverIcon` |

Réglage partagé hors section, catégorie « Ad blocking » / « Blocage des publicités » : « Enable IPv6 support » / « Activer la prise en charge d'IPv6 », interrupteur, désactivé par défaut (clé `enableIpv6`). Il sert aussi au mode VPN.

### Règles des réglages
- **Redirection IPv4** : la valeur doit être une adresse IPv4 écrite en chiffres (pas de nom d'hôte, pas de valeur vide). Si invalide : message court (toast) EN « Invalid redirection » / FR « La redirection est invalide », et la nouvelle valeur est refusée (l'ancienne reste).
- **Redirection IPv6** : même règle avec une adresse IPv6. Champ actif seulement si le support IPv6 est activé au moment de l'ouverture de l'écran.
- Changer une redirection ou le support IPv6 ne réapplique rien : le nouveau réglage ne compte qu'à la prochaine application du hosts.
- **Hide reboot dialog** : réglage stocké mais sans aucun effet (aucune boîte de redémarrage n'existe plus).
- **Ouvrir le fichier hosts** : l'app résout le chemin réel de `/system/etc/hosts` (en suivant les liens symboliques). Si le fichier n'est pas modifiable, elle remonte sa partition en écriture, puis demande à Android d'ouvrir ce fichier comme texte brut dans une autre app. Au retour de cette app, la partition est remontée en lecture seule (seulement si elle avait été remontée). Si aucune app ne sait ouvrir un texte : boîte « No text editor installed » / « Aucun éditeur de texte n'est installé », texte « No text editor could be found to open the hosts file. You can install Jota or other text editors to handle this.\n\nDo you want to install Jota? » / « Aucun éditeur de texte n'a été trouvé pour ouvrir le fichier hosts. Vous pouvez installer Jota ou d'autres éditeurs de texte pour ce faire.\n\nVoulez-vous installer Jota ? », bouton « Yes » qui ouvre la fiche de Jota dans la boutique (voir défauts : cette fonction ne marche pas en l'état).

## Fichier hosts : où et comment il est écrit

### Emplacement
- Cible unique : `/system/etc/hosts`. Aucune autre cible, aucun réglage pour la changer.
- Le fichier est d'abord construit dans le stockage privé de l'app (fichier nommé `hosts`), puis copié vers la cible avec les droits root.

### Étapes d'une application (« apply »)
Messages d'état affichés successivement (zone d'état de l'accueil) :
1. EN « Apply latest sources » / FR « Appliquer les sources les plus récentes »
2. EN « Creating a new hosts file » / FR « Création d'un nouveau fichier hosts » : l'ancien fichier privé est supprimé puis le nouveau est écrit (contenu décrit plus bas).
3. EN « Copying a new hosts file » / FR « Copie d'un nouveau fichier hosts » :
   - Vérification d'espace : la taille du nouveau fichier est comparée à l'espace libre de la partition de la cible. Si l'espace libre est connu et inférieur ou égal à la taille, erreur « Not enough space ». Si l'espace libre est inconnu (lu comme 0), la copie est tentée quand même.
   - Vérification d'écriture : la cible est-elle modifiable par l'app, sinon par root.
   - Si elle n'est pas modifiable : l'app cherche le point de montage qui contient la cible (la cible elle-même ou le dossier parent le plus proche listé dans les montages actifs) et le remonte en lecture-écriture. Si aucun point de montage n'est trouvé ou si le remontage échoue : erreur « Failed to install new hosts file ».
   - Copie : le contenu du fichier privé est écrit **dans** le fichier cible existant (réécriture du contenu, sans supprimer ni remplacer le fichier), ce qui garde valide un fichier recouvert par un module Magisk. Puis propriétaire fixé à root:root (0:0) et droits fixés à 644 (lecture pour tous, écriture pour root).
   - Si une de ces trois commandes échoue : erreur « Failed to install new hosts file ».
   - Dans tous les cas (succès ou échec), si un remontage en écriture a été fait, la partition est remontée en lecture seule à la fin.
4. EN « Checking hosts file copy » / FR « Vérification de la copie du fichier hosts » : message affiché, **aucune vérification réelle n'est faite**.
5. EN « Hosts file successfully updated » / FR « Le fichier hosts a été mis à jour avec succès ». L'état « blocage actif » passe à vrai.

Le fichier privé `hosts` n'est pas supprimé après la copie (il est remplacé à la prochaine application).

### Qui déclenche une application
- Bouton principal de l'accueil, tuile des réglages rapides, commande externe « START » envoyée par une autre app autorisée : application inconditionnelle.
- Synchronisation manuelle, bandeau « Your configuration changed. You need to apply it. » / « Votre configuration a changé. Vous devez l'appliquer. » (bouton « Apply » / « Appliquer »), mise à jour automatique quotidienne des sources : application **seulement si** le hosts actuel est déjà géré par l'app (voir « État appliqué »). Ces chemins ne doivent jamais réactiver un blocage que l'utilisateur a coupé.
- Pendant une opération en cours, un second appui sur le bouton principal est ignoré.

## Contenu exact du fichier hosts généré

Encodage UTF-8, fin de ligne `\n`, séparateur unique : un espace entre l'adresse et le nom.

Ordre strict :
1. `# This hosts file has been generated by AdAway on: AAAA-MM-JJ HH:MM:SS` (date et heure locales de l'appareil au moment de la génération, 24 h, sans fuseau horaire).
2. `# Please do not modify it directly, it will be overwritten when AdAway is applied again.`
3. `# This file is generated from the following sources:`
4. Une ligne par source **activée**, hors liste utilisateur, triée par URL croissante : `# - <nom de la source>:<url>` (pas d'espace après les deux-points). Aucune ligne si aucune source.
5. Une ligne vide.
6. `127.0.0.1 localhost`
7. `::1 localhost`
8. Les entrées effectives, triées par nom d'hôte croissant (ordre binaire des caractères) :
   - Hôte bloqué : `<redirection IPv4> <hôte>` puis, si le support IPv6 est activé, `<redirection IPv6> <hôte>` juste en dessous.
   - Hôte redirigé : une seule ligne `<adresse de redirection propre à l'hôte> <hôte>`, quel que soit le réglage IPv6.
   - Hôte autorisé : absent du fichier (il a été retiré des entrées effectives, y compris via les jokers `*` et `?` des règles d'autorisation ; la construction de ces entrées relève de la zone « listes »).

Les textes de l'en-tête restent en anglais quelle que soit la langue (ils servent aussi à reconnaître le fichier, voir ci-dessous).

Exemple avec IPv6 activé, redirections par défaut :
```
# This hosts file has been generated by AdAway on: 2026-10-14 09:30:12
# Please do not modify it directly, it will be overwritten when AdAway is applied again.
# This file is generated from the following sources:
# - AdAway official hosts:https://adaway.org/hosts.txt

127.0.0.1 localhost
::1 localhost
127.0.0.1 ads.example.com
::1 ads.example.com
192.168.1.10 printer.example.com
```

## État appliqué (le hosts est-il celui de l'app ?)

- À la création du mode root (ouverture de l'app en mode root, ou passage au mode root), l'app lit via root la première ligne de `/system/etc/hosts`.
- Le blocage est considéré « actif » si cette ligne commence par `# This hosts file has been generated by AdAway on: `. Sinon, ou si la lecture échoue, il est « inactif ».
- Cette vérification ne contrôle ni la date, ni les sources, ni le contenu : elle dit seulement « fichier écrit par l'app ou non ». Il n'y a aucune comparaison entre le hosts installé et les listes actuelles. La fraîcheur des sources est gérée ailleurs (zone mises à jour des sources).
- L'état n'est relu qu'à cette création. Un changement extérieur (module Magisk désactivé puis redémarrage, fichier modifié à la main) n'est vu qu'au prochain lancement de l'app.
- Après une application réussie l'état passe à « actif », après un retour à l'origine réussi à « inactif ». En cas d'échec, l'état ne change pas.
- Cet état pilote la couleur de l'en-tête de l'accueil (couleur principale si actif, gris sinon), l'icône du bouton principal (pause si actif, logo sinon) et l'état de la tuile.

## Retour à l'état d'origine (désactivation)

Déclenché par le bouton principal quand le blocage est actif, la tuile, ou la commande externe « STOP ».

- Message EN « Reverting to default hosts file » / FR « Rétablissement du fichier hosts par défaut ».
- L'app écrit un fichier privé `default_hosts` contenant exactement :
  ```
  127.0.0.1 localhost
  ::1 localhost
  ```
- Puis elle le copie vers `/system/etc/hosts` avec exactement la même procédure que l'application (vérification d'espace, remontage si besoin, réécriture du contenu, propriétaire 0:0, droits 644, remontage en lecture seule).
- Le fichier privé `default_hosts` est supprimé après une copie réussie.
- Message EN « Default hosts file restored » / FR « Le fichier hosts par défaut a été restauré », état « inactif ».
- Le fichier d'origine de l'appareil n'est **pas** sauvegardé avant la première application : le retour à l'origine écrit toujours ce contenu minimal, pas le fichier d'avant.
- Tout échec (y compris manque d'espace) donne l'erreur « Failed to revert hosts file ».

## Messages d'erreur du mode root

Présentation : boîte de dialogue avec icône d'alerte, titre = message court, texte = détail suivi d'une ligne vide puis EN « Please read Help for more information. » / FR « Pour plus de précisions, consultez l'aide. ». Boutons : « Help » / « Aide » (ouvre l'aide) et « Close » / « Fermer ». Sur l'écran d'accueil de premier lancement, seul le message court est montré dans l'écran de synchronisation.

| Cas | Titre EN / FR | Détail EN / FR |
|---|---|---|
| Écriture du fichier privé impossible | « Failed to create private file » / « Échec de création du fichier privé » | « Unable to create a private file to build a new hosts source. Please check the free space available on your device. » / « Impossible de créer un fichier privé pour construire une nouvelle source de fichiers hosts. Vérifiez l'espace libre sur votre appareil. » |
| Espace insuffisant sur la partition cible | « Not enough space » / « Il manque d'espace » | « Unable to copy hosts file to your system partition. Please check Magisk "systemless hosts" module is enabled then reboot. » / « Impossible de copier le fichier hosts vers votre partition système. Assurez-vous que le module « Systemless Hosts » de Magisk est activé, puis redémarrez. » |
| Remontage ou copie impossible | « Failed to install new hosts file » / « Échec d'installation du nouveau fichier hosts. » | « Unable to copy hosts file to /system partition. Please check Magisk "systemless hosts" module is enabled then reboot. » / « Impossible de copier le fichier hosts vers la partition /system. Assurez-vous que le module « Systemless Hosts » de Magisk est activé, puis redémarrez. » |
| Retour à l'origine impossible | « Failed to revert hosts file » / « Échec de rétablissement du fichier hosts » | « Unable to restore default hosts file configuration. » / « Impossible de restaurer la configuration du fichier hosts par défaut. » |

Depuis la tuile ou une commande externe, l'échec n'est pas affiché (la tuile revient simplement à l'état réel).

Aide intégrée (onglet Problèmes), section « Applying the hosts file fails (root mode) » : les Android récents ont une partition /system en lecture seule ; avec Magisk il faut activer le module systemless hosts, redémarrer et réappliquer ; sans root, passer en mode VPN.

## Serveur web local

### Rôle
Texte descriptif EN « The local web server listens on local IP addresses to answer to blocked hostname requests. It may help with app freezing on blocked connection. » / FR « Le serveur Web local écoute les adresses IP locales pour répondre aux requêtes vers des noms d'hôtes bloqués. Cela pourrait empêcher le gel de certaines applications lors de connexions bloquées. »

Comme les hôtes bloqués pointent vers 127.0.0.1, une app qui tente de charger une pub se connecte à l'appareil lui-même. Sans serveur, la connexion est refusée ; avec le serveur, elle reçoit une réponse vide immédiate (ou une icône).

### Écoute
- Processus séparé lancé avec les droits root, détaché de l'app (il survit à la fermeture de l'app et à une mise à jour de l'app).
- Ports : HTTP 80 et HTTPS 443, sur `localhost` résolu en **127.0.0.1 uniquement** (pas d'écoute sur ::1, ni sur les autres adresses du téléphone).
- Si l'un des deux ports ne peut pas être ouvert (déjà pris), le serveur s'arrête immédiatement.
- Il s'arrête proprement sur signal d'interruption ou de fin.
- Option de débogage côté serveur (journal détaillé) existante mais jamais activée par l'app.

### Réponses (toute méthode, tout nom d'hôte demandé)
- Chemin exactement `/internal-test` : renvoie la page de test.
- Sinon, si l'option icône est activée : renvoie l'icône SVG de l'app, quel que soit le chemin demandé.
- Sinon : réponse 200 OK au corps vide.
- Page de test (titre « Web server test page ») : titre « Web server works fine », image `icon.png` (servie comme l'icône SVG quand l'option icône est active, vide sinon), texte « If you can see this page, that means the web server is working fine and its certificate is installed. ». Page en anglais seulement.

### Fichiers du serveur
- À chaque démarrage, l'app recopie depuis le paquet, en écrasant les anciennes copies, dans un dossier privé `webserver` : le certificat, sa clé privée, l'icône SVG, la page de test. Un échec de copie est seulement journalisé, le démarrage est tenté quand même.

### Certificat HTTPS fourni
- Certificat autosigné RSA 2048, SHA-256, nom `localhost`.
- Noms couverts : `localhost`, `*.doubleclick.net`, `*.g.doubleclick.net`, adresse 127.0.0.1.
- Validité : du 11/09/2026 au 11/10/2029.
- Marqué comme autorité de certification, usage « signature numérique » et « authentification serveur TLS ».
- Le certificat et sa clé privée sont identiques pour toutes les installations (voir défauts).

### Démarrage, arrêt, redémarrage
- Interrupteur « Enable web server » activé : l'app arrête toute instance existante (y compris une vieille instance laissée par une installation précédente), recopie les fichiers, lance le serveur (avec l'icône si l'option est activée). Désactivé : l'app arrête le serveur.
- 500 ms après le changement, l'app vérifie si le processus tourne. S'il ne correspond pas à l'interrupteur, l'interrupteur est remis à l'état réel (et ce nouvel état est enregistré), sans message.
- Changer l'option icône alors que le serveur tourne : arrêt puis redémarrage immédiat avec la nouvelle option, puis rafraîchissement de l'état.
- Au démarrage du téléphone : si le mode est root et l'interrupteur activé, le serveur est lancé.
- À la création du mode root dans l'app (ouverture de l'app en mode root) : si l'interrupteur est activé et que le serveur ne tourne pas, il est relancé.
- S'il est tué en cours de route : aucune surveillance ; il n'est relancé qu'au prochain démarrage du téléphone ou à la prochaine ouverture de l'app en mode root.
- Priorité mémoire : au lancement, le serveur abaisse sa propre note de « victime en cas de manque de mémoire » à -17 si elle est plus élevée (échec seulement journalisé). Voir défauts : effet quasi nul.
- « Tourne » signifie : un processus portant le nom de l'exécutable du serveur apparaît dans la liste des processus.

### État affiché (sous-titre de « Test web server »)
- Rafraîchi à l'ouverture de l'écran Préférences et à chaque retour sur cet écran, seulement en mode root, et après chaque changement de l'interrupteur ou de l'option icône.
- Affiche d'abord EN « Checking… » / FR « Vérification… », attend 500 ms, puis l'app interroge `https://localhost/internal-test` (l'app fait confiance aux certificats installés par l'utilisateur pour `localhost` et ses sous-domaines).
  - Réponse réussie : EN « Running and certificate installed » / FR « En fonction et le certificat est installé ».
  - Échec de la négociation TLS : EN « Running but certificate not installed » / FR « En fonction, mais le certificat n'est pas installé ».
  - Connexion refusée, autre erreur, ou réponse non réussie : EN « Not running » / FR « N'est pas en fonction ».
- Toucher « Test web server » ouvre `https://localhost/internal-test` dans le navigateur.

### Installation du certificat (« Install self-signed certificate »)
- Avant Android 11 : l'app ouvre l'écran système d'installation de certificat avec le certificat fourni, nommé « AdAway ».
- À partir d'Android 11 : l'app propose d'enregistrer un fichier (nom proposé `adaway-webserver-certificate.crt`, type certificat X.509 d'autorité). Si l'utilisateur annule, rien ne se passe. Sinon le certificat est écrit dans le fichier choisi puis une boîte s'affiche :
  - Titre EN « Certificate manual install » / FR « Installation manuelle d'un certificat ».
  - Texte EN « Starting from Android 11 (R), application can no more automatically install certificate authority (CA).\n\nGo to "Security" settings, "Encryption & credentials" then "Install a certificate". From there, choose "CA certificate" and select the newly exported certificate file. » / FR « À partir d'Android 11 Ⓡ, une application ne peut plus installer manuellement une autorité de certification (CA).\n\nAccédez aux paramètres Sécurité > Chiffrement et authentifiants, puis Installer un certificat. Choisissez ensuite Certificat CA et sélectionnez le fichier du certificat nouvellement exporté. »
  - Bouton EN « Open "Security" settings » / FR « Ouvrir les paramètres « Sécurité » » qui ouvre les réglages de sécurité d'Android. Boîte annulable.
- Un échec de lecture ou d'écriture du certificat n'est que journalisé : aucun message à l'utilisateur, et la boîte d'explication s'affiche quand même.

## Journal DNS en mode root

### Ce qui est capturé
- Outil de capture réseau fourni avec l'app, lancé avec les droits root, détaché de l'app (continue après fermeture de l'app).
- Écoute sur toutes les interfaces, sans mode promiscuité, sortie ligne par ligne, détaillée, sans horodatage, 512 premiers octets de chaque paquet.
- Filtre : uniquement les paquets **UDP à destination du port 53** (requêtes DNS classiques en clair). Ne capture pas le DNS sur TCP, le DNS chiffré (DNS privé d'Android, DNS sécurisé des navigateurs) ni les réponses.
- Toute la sortie (y compris les messages d'erreur de l'outil) est ajoutée à la fin d'un fichier `dns_log.txt` dans le cache de l'app, créé s'il n'existe pas. Le fichier n'a pas de limite de taille.
- En mode root avec le blocage actif, les hôtes bloqués sont résolus localement par le hosts : aucune requête DNS ne part, donc ils n'apparaissent pas. L'écran vide l'indique : EN « \n\nBlocked requests will not be logged. Disable ad-blocking first if you want to log them too. » / FR « \n\nLes requêtes bloquées ne seront pas journalisées. Désactivez d'abord le blocage des publicités si vous voulez aussi les journaliser. » (ajouté au texte d'invite habituel, uniquement en mode root).

### Enregistrement
- « Enregistrement en cours » signifie : le processus de capture apparaît dans la liste des processus. Cet état survit donc à la fermeture de l'app.
- Démarrer : crée le fichier si besoin puis lance la capture. Arrêter : tue la capture.
- Effacer : vide le fichier (la capture éventuelle continue d'y écrire). Si le fichier n'existe pas, rien à faire.

### Format du résultat
- Seules les lignes contenant une question de type `A?` ou `AAAA?` suivie d'un nom terminé par un point sont retenues ; le nom est extrait sans le point final.
- Les doublons sont supprimés (première apparition gardée), puis chaque nom reçoit son statut actuel (bloqué, redirigé ou aucun) d'après les entrées effectives.
- Tri par défaut « Top level domain sort » / « Tri par domaines de premier niveau » (comparaison des morceaux du nom de droite à gauche ; à égalité, le nom le plus court d'abord). Le menu bascule vers « Alphabetical sort » / « Tri alphabétique » et inversement, avec un message court donnant le nom du tri choisi.
- L'affichage de la liste (ajout aux listes, copie, ouverture dans le navigateur, bandeau « configuration changée ») est commun aux deux modes et relève de la zone « journal DNS ». Mobile : rechargement à l'ouverture et par balayage vers le bas. TV : rechargement automatique chaque seconde pendant l'enregistrement.

## Défauts ou bizarreries constatés (à ne pas reproduire)

- **Clé privée du certificat publique** : la même clé est livrée dans chaque paquet de l'app. N'importe qui peut la récupérer et se faire passer pour `localhost`, `*.doubleclick.net` et `*.g.doubleclick.net` auprès de tout appareil qui a installé ce certificat. Il faut une clé générée sur l'appareil, propre à chaque installation.
- **Certificat marqué « autorité de certification »** alors qu'il ne sert qu'à un serveur : combiné à la clé publique, c'est une surface d'attaque inutile. Le fait que l'usage « signature de certificats » soit absent limite probablement le risque, mais ce n'est pas un choix sain.
- **Certificat à date fixe** (fin le 11/10/2029) : après expiration, l'état affichera « certificat non installé » à tort et le HTTPS échouera.
- **Noms doubleclick dans le certificat** : choix arbitraire, sans intérêt pour les autres domaines bloqués (le HTTPS vers eux échouera de toute façon).
- **Serveur web uniquement en IPv4** : avec le support IPv6 actif, les lignes `::1` envoient les apps vers une adresse où rien n'écoute. Le texte descriptif parle d'« adresses IP locales » au pluriel, ce qui est faux.
- **Priorité mémoire inefficace** : la valeur -17 vient de l'ancienne échelle (-17 à 15) mais est écrite dans le réglage moderne (échelle -1000 à 1000), où elle ne protège presque pas le processus.
- **Aucune surveillance du serveur web** : s'il meurt, il reste mort jusqu'à la prochaine ouverture de l'app ou au prochain démarrage, alors que l'interrupteur reste sur « activé ».
- **Échec de démarrage silencieux** (port pris, fichiers manquants) : l'interrupteur se remet à « désactivé » sans aucune explication.
- **Serveur web non arrêté au changement de mode** : passer du mode root au mode VPN laisse le serveur tourner en root.
- **Détection de processus fragile** : « tourne » est déduit d'une recherche de nom dans la liste des processus, sans identifiant de processus mémorisé ; l'arrêt tue tous les processus de ce nom.
- **Fausse étape « Checking hosts file copy »** : annoncée mais aucune vérification n'est faite. Il faut relire le fichier installé et le comparer à ce qui a été écrit.
- **État « appliqué » trop pauvre** : seule la première ligne est regardée, une seule fois au démarrage. Un fichier tronqué ou modifié est vu comme correct ; un changement extérieur n'est vu qu'au prochain lancement.
- **Course au démarrage** : l'état « appliqué » est calculé en arrière-plan à l'ouverture. Une mise à jour automatique lancée juste après peut le lire encore « inconnu » et sauter l'application à tort.
- **Pas de sauvegarde du hosts d'origine** : le retour à l'origine écrit un contenu minimal (`127.0.0.1 localhost`, `::1 localhost`) qui diffère du fichier d'Android (par exemple `::1 ip6-localhost`) et efface toute entrée personnelle préexistante.
- **Vérification d'espace approximative** : un espace libre inconnu est traité comme suffisant, et l'espace déjà occupé par l'ancien fichier n'est pas compté.
- **Remontage de partition sur Android moderne** : le remontage en écriture échoue presque toujours (système en lecture seule vérifié) ; l'erreur affichée parle alors de « copie », pas de remontage. Le message dédié « Remount failed » existe mais n'est jamais utilisé.
- **Ouvrir le fichier hosts : fonction cassée** : l'app transmet un chemin de fichier brut à une autre app, ce que les versions d'Android ciblées interdisent (plantage très probable). Même si l'éditeur s'ouvrait, il n'aurait pas les droits root pour enregistrer. La partition peut rester en écriture si l'app plante. La proposition d'installer « Jota » renvoie vers une app ancienne.
- **Champ de redirection IPv6** : il n'est activé ou désactivé qu'à l'ouverture de l'écran ; activer IPv6 ne le débloque pas tant qu'on ne quitte pas l'écran.
- **Changer la redirection n'annonce rien** : aucun bandeau « configuration changée », l'utilisateur ne sait pas qu'il faut réappliquer.
- **Réglage mort** « Hide reboot dialog after apply » : visible et stocké, sans effet.
- **Textes orphelins** (cibles, chemin personnalisé, liens symboliques, systemless, redémarrage, root refusé) : à ne pas reprendre tels quels.
- **Retrait du root non signalé** : aucune vérification ni message spécifique, l'utilisateur voit une erreur de copie trompeuse.
- **Noms de sources injectés dans les commentaires** : le nom d'une source est écrit tel quel ; un nom contenant un retour à la ligne pourrait ajouter des lignes actives au hosts. Il faut nettoyer ces textes.
- **Erreurs de certificat silencieuses** : un échec d'export du certificat affiche quand même la boîte « installez le fichier exporté ».
- **Journal DNS** :
  - Le bouton d'enregistrement passe à « en cours » même si la capture n'a pas démarré.
  - Le fichier de capture grossit sans limite et contient aussi les messages d'erreur de l'outil.
  - Seules les questions A et AAAA sont retenues (les questions de type HTTPS/SVCB, fréquentes sur Android récent, sont ignorées).
  - Une vérification de présence de l'outil de capture système est faite au démarrage mais ne sert qu'au journal interne.
  - Sur TV, l'enregistrement est désactivé et forcé à l'arrêt quand le blocage est inactif (logique pensée pour le VPN) : en mode root c'est l'inverse de ce que conseille le message (désactiver le blocage pour tout voir).
  - Le statut « autorisé » n'est jamais affiché pour un nom du journal, car les hôtes autorisés ne figurent pas dans les entrées effectives ; toucher « autoriser » crée alors un doublon.
- **Page de test** : référence une image `icon.png` qui n'existe pas (elle ne s'affiche que par accident quand l'option icône renvoie l'icône pour tout chemin) ; page en anglais seulement.

## Questions ouvertes

- Faut-il réintroduire un choix de cible (chemin personnalisé, `/data/...` avec lien symbolique) ou garder la cible unique `/system/etc/hosts` en s'appuyant sur les modules systemless ?
- Faut-il que l'app détecte elle-même la présence d'un module systemless hosts (Magisk, KernelSU, APatch) et guide l'utilisateur, voire installe un module, plutôt que de se contenter d'un message d'erreur ?
- Faut-il tenter un remontage de partition sur les Android récents, ou le supprimer et exiger un module systemless ?
- Faut-il sauvegarder le hosts d'origine à la première application pour le restaurer à l'identique à la désactivation ?
- Quelle vérification après copie : relecture complète, empreinte du contenu, simple contrôle de taille et d'en-tête ?
- L'état « appliqué » doit-il être revérifié régulièrement (à chaque retour au premier plan, après démarrage) et signaler un hosts modifié de l'extérieur ?
- Le format de l'en-tête doit-il rester identique (pour reconnaître les fichiers déjà installés par l'ancienne app lors d'une mise à jour) ? Faut-il ajouter le fuseau horaire ?
- Faut-il garder la ligne `::1 localhost` ou reprendre la forme d'Android `::1 ip6-localhost` ?
- Serveur web : faut-il écouter aussi sur ::1, générer un certificat propre à chaque appareil, le limiter à `localhost`, ajouter une surveillance et un redémarrage automatique, et afficher la raison d'un échec de démarrage ?
- Serveur web : le garder en processus root séparé, ou le faire tourner autrement (les ports 80 et 443 exigent root ou une capacité spéciale) ?
- Journal DNS : capturer aussi DNS sur TCP et les autres types de questions, limiter la taille du fichier, arrêter la capture quand l'app quitte le mode root ?
- Les réglages « Hide reboot dialog » et les textes orphelins sont-ils à supprimer définitivement ?
- Faut-il revérifier le root à chaque opération et afficher un message dédié « root refusé » (les textes « Root access denied » / « Allow root access from your root app » existent) ?
- Délai d'attente maximal de la demande de root : quelle valeur imposer ?
- Faut-il conserver la compatibilité des clés de stockage des réglages pour une mise à jour par-dessus l'ancienne app ?

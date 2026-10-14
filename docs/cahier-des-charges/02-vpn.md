# 02. Mode VPN (blocage par VPN local)

Ce document décrit le comportement fonctionnel attendu du mode VPN, tel qu'observé dans l'application de référence, corrigé de ses défauts. Les comportements de l'ancienne app jugés défectueux sont décrits dans "Défauts ou bizarreries constatés" et ne doivent pas être reproduits.

## 1. Principe et périmètre

- Le mode VPN bloque les publicités sans root. L'app crée un VPN local (aucun serveur distant) qui ne capture QUE le trafic DNS envoyé à des adresses de serveurs DNS virtuelles. Tout le reste du trafic des apps ne passe pas par le tunnel.
- Pour chaque nom demandé, l'app consulte la table compilée des règles (bloqué, autorisé, redirigé). Le calcul de cette table (sources, listes, jokers, priorité autorisé sur bloqué) relève de la zone "listes et sources". Le VPN fait une recherche par nom exact, en minuscules.
- Le mode VPN est un des deux modes de blocage (l'autre est le mode root). Tout ce qui suit ne s'applique que lorsque le mode choisi est VPN. Les réglages VPN sont grisés dans les préférences quand le mode actif est root.
- Android 8 minimum.

## 2. Démarrage et arrêt

### 2.1 Intention de l'utilisateur et état réel

L'app distingue deux informations persistées, à ne jamais confondre :

- **Intention utilisateur** ("l'utilisateur veut le VPN allumé") : oui ou non. Elle seule autorise un redémarrage automatique en arrière-plan.
- **État du service** : arrêté, démarrage, actif, arrêt en cours, attente réseau, reconnexion, erreur de reconnexion (voir section 14).

Règles :

- Une action explicite de l'utilisateur pour allumer met l'intention à "oui". Une action explicite pour éteindre la met à "non".
- Aucun mécanisme automatique (surveillance, battement périodique, mise à jour des listes, résurrection par le système, changement de réseau) ne doit jamais rallumer le VPN si l'intention est "non".
- Si l'intention n'a jamais été enregistrée (première lecture après migration), elle vaut "oui" si l'état persisté était un état démarré, "non" sinon.
- Si l'état persisté dit "démarré" mais que le service n'existe plus réellement (processus tué), l'état est corrigé à "arrêté" dès qu'on le lit, pour que l'interface dise la vérité.

### 2.2 Ce qui allume le VPN (intention mise à "oui")

- Bouton principal de l'écran d'accueil (téléphone et TV).
- Tuile des réglages rapides.
- Action "Reprendre" de la notification.
- Commande externe "START" reçue d'une autre app (voir 2.5).
- Fin de l'assistant de premier lancement TV (après une synchronisation réussie des sources).
- Démarrage de l'appareil si "Activer au démarrage" est actif (voir section 11).

Toute action explicite de l'utilisateur démarre le tunnel immédiatement, sans délai de limitation (le compteur de limitation est remis à zéro, voir 10.3).

### 2.3 Ce qui allume le VPN sans toucher à l'intention (seulement si intention = "oui")

- Mise à jour des listes en arrière-plan ou application d'une nouvelle configuration : le cache des décisions est vidé ; si le VPN tourne il continue (les nouvelles règles sont prises à la volée), s'il ne tourne pas et que l'intention est "oui", il est démarré.
- Battement périodique (10.4), ouverture de l'app après un arrêt forcé (10.5), résurrection par le système (10.6).

### 2.4 Ce qui éteint le VPN

- Bouton principal, tuile, action "Pause" de la notification, commande externe "STOP" : intention mise à "non", battement périodique annulé, tunnel fermé, service arrêté, notification de premier plan retirée.
- Révocation par Android parce qu'une autre app VPN a pris la place (voir 8.1) : intention mise à "non".
- "Pause" signifie un arrêt complet jusqu'à une reprise manuelle. Il n'existe pas de pause temporisée.

Exigences :

- Une seule pression sur Pause doit suffire. Le tunnel fermé volontairement ne doit jamais être interprété comme une panne réseau déclenchant une reconnexion.
- Un événement réseau qui arrive juste après un arrêt volontaire (par exemple une perte de Wi-Fi dans la seconde qui suit) ne doit pas rallumer le VPN.

### 2.5 Commande externe

- Une autre app peut envoyer une commande "START" ou "STOP" via une action de diffusion dédiée, à condition d'avoir obtenu une permission spécifique de l'app (niveau "dangereux", donc accordée par l'utilisateur).
- Libellés de la permission : "Send commands to AdAway" / "Allow to send commands to AdAway like enabling or disabling the system-wide ad-blocking" (groupe "AdAway permissions" / "Allow to interact with AdAway").
- La commande s'applique au mode de blocage actif (VPN ou root).

## 3. Autorisation VPN Android

- Android exige un consentement unique de l'utilisateur ("demande de connexion VPN" système) avant qu'une app puisse créer un VPN. Ce consentement est perdu si une autre app VPN est autorisée ensuite.
- Moments où l'app vérifie et demande le consentement :
  - Assistant de premier lancement (téléphone) : au choix de la carte "VPN". Consentement accordé : le mode VPN est retenu et "Suivant" est autorisé. Refusé : aucun mode retenu, "Suivant" bloqué, et si un autre VPN est réglé en "VPN permanent", une boîte explique le blocage (textes en 18.5).
  - Assistant TV : au bouton d'activation et à chaque nouvel essai après une erreur.
  - Ouverture de l'écran d'accueil (téléphone et TV) en mode VPN si le consentement manque.
- Exigences pour la réécriture :
  - Après un consentement accordé depuis l'accueil, si l'intention est "oui", démarrer le VPN tout de suite.
  - Un refus ne doit pas relancer la demande en boucle : la redemander au plus une fois par ouverture de l'app, ou sur action de l'utilisateur.
  - Tout démarrage (bouton, tuile, notification, démarrage de l'appareil, battement) doit d'abord vérifier le consentement. S'il manque : ne pas lancer de tunnel en boucle ; afficher l'état "autorisation requise" et proposer d'ouvrir l'app pour l'accorder (une app en arrière-plan ne peut pas afficher la demande elle-même).
  - Message d'échec d'activation existant : "Failed to enable VPN ad blocking." / "Please check your VPN settings to authorize the application VPN to start."

## 4. Ce qui est intercepté

### 4.1 Adresses virtuelles

- Le tunnel reçoit une adresse IPv4 prise dans une plage réservée à la documentation : 192.0.2.1/24 (TEST-NET-1). 198.51.100.0/24 puis 203.0.113.0/24 ne servent que si l'ajout de la première échoue.
- Une adresse IPv6 dans 2001:db8::/120 (préfixe de documentation) n'est ajoutée que si au moins un serveur DNS IPv6 est retenu (voir 6.3).
- Chaque serveur DNS réel retenu reçoit une adresse virtuelle. La numérotation est commune aux deux familles, dans l'ordre de la liste : le 1er serveur reçoit l'hôte .2, le 2e l'hôte .3, etc. Exemple : [IPv4 A, IPv6 B, IPv4 C] donne 192.0.2.2, 2001:db8::3, 192.0.2.4.
- Ces adresses virtuelles sont déclarées à Android comme serveurs DNS du VPN. Une route /32 est ajoutée pour chaque adresse virtuelle IPv4 ; les adresses IPv6 sont dans le /120 du tunnel.
- Il n'y a pas de route par défaut : seul le trafic vers ces adresses entre dans le tunnel.

### 4.2 Paramètres du tunnel

- Nom de session affiché par Android : "AdAway".
- Le bouton "configurer" du VPN dans les réglages Android ouvre l'écran d'accueil de l'app.
- Les apps peuvent contourner le VPN en se liant explicitement à un réseau (contournement autorisé, pour compatibilité).
- Le VPN hérite du caractère facturé à l'usage ("metered") des réseaux sous-jacents (Android 10 et plus), pour ne pas faire croire aux apps qu'elles sont sur un réseau illimité, ni l'inverse.
- Les deux familles IPv4 et IPv6 restent autorisées, même si aucun serveur DNS d'une famille n'est trouvé, pour ne bloquer aucune famille.
- L'app elle-même n'est jamais exclue explicitement (ses propres requêtes DNS passent donc par le filtre).

### 4.3 Ce qui n'est pas intercepté (limites à connaître)

- Les apps qui utilisent leur propre DNS en dur (ex. 8.8.8.8), le DNS chiffré intégré à une app (DoH, DoT), ou le "DNS privé" d'Android en mode strict, échappent au filtre.
- Le DNS en TCP vers les adresses virtuelles est ignoré (paquet abandonné). Seul l'UDP est traité.
- Les apps exclues (section 9) ne sont pas filtrées.

## 5. Traitement d'une requête DNS

### 5.1 Filtrage des paquets reçus du tunnel

Dans l'ordre :

1. Paquet IP illisible : abandonné sans réponse.
2. Protocole autre qu'UDP : abandonné sans réponse.
3. Adresse de destination qui ne correspond à aucun serveur virtuel connu : abandonné (journalisé en avertissement).
4. UDP sans contenu (Firefox envoie un datagramme vide pour "chauffer" la connexion) : un datagramme vide est relayé vers le vrai serveur DNS correspondant, sans attendre de réponse.
5. Contenu qui n'est pas un message DNS valide, ou message sans question : abandonné sans réponse. Un paquet malformé ne doit JAMAIS faire tomber le tunnel.
6. Sinon, seule la première question est examinée. Le nom (sans point final, en minuscules) est cherché dans la table des règles. Un nom absent est "autorisé".

Le port de destination d'origine est conservé lors du relais (normalement 53).

### 5.2 Nom bloqué

- Réponse locale immédiate, sans contacter Internet : copie de la requête avec le drapeau "réponse", code NOERROR, aucune réponse, et un enregistrement SOA dans la section "autorité" pour la mise en cache négative.
- SOA : nom "adaway.vpn.invalid." (nom volontairement invalide), TTL 5 secondes, minimum 5 secondes, autres valeurs à 0.
- Résultat pour l'app : "le nom existe mais n'a pas d'adresse" (NODATA), pour tout type de requête (A, AAAA, HTTPS, MX...).
- TTL court (5 s) choisi pour qu'un nom débloqué par l'utilisateur fonctionne presque tout de suite.

### 5.3 Nom redirigé

- Réponse locale immédiate : drapeau "réponse", drapeau "faisant autorité", code NOERROR, un enregistrement de réponse vers l'adresse de redirection, TTL 5 secondes.
- Adresse IPv4 : enregistrement A. Adresse IPv6 : enregistrement AAAA.
- Exigence pour la réécriture : ne répondre avec l'adresse que si le type demandé correspond (A pour IPv4, AAAA pour IPv6). Pour les autres types, répondre NODATA comme pour un nom bloqué (voir défauts).
- Si l'adresse de redirection est invalide : réponse NOERROR vide (sans réponse). Les adresses sont validées à la saisie (zone listes).

### 5.4 Nom autorisé

- La requête brute est relayée telle quelle vers le vrai serveur DNS associé à l'adresse virtuelle visée, par une connexion qui sort du VPN (protégée) et qui n'accepte de réponse QUE de ce serveur (protection contre les réponses usurpées).
- La première réponse reçue est renvoyée à l'app sans modification, en inversant adresses et ports.
- Taille de réponse acceptée : jusqu'à 4096 octets (taille maximale annoncée par les clients EDNS0). Une réponse ne doit jamais être tronquée par l'app (l'ancienne limite de 1024 octets cassait les grosses réponses).
- Délai d'attente d'une réponse : 10 secondes. Passé ce délai, la requête est oubliée sans réponse (l'app cliente gère son propre délai et réessaie).
- Au plus 1024 requêtes en attente en même temps. Au-delà, la plus ancienne est abandonnée pour faire place.
- À chaque reconstruction du tunnel, toutes les requêtes en attente sont abandonnées (elles ont été envoyées sur l'ancien réseau).
- Si l'envoi échoue parce que le réseau est injoignable ou refusé : le tunnel est considéré en panne réseau et se reconnecte (10.3). Toute autre erreur d'envoi : la requête est abandonnée, le tunnel continue.

### 5.5 Cache des décisions

- Les décisions de la table des règles sont mises en cache en mémoire (4096 noms au plus, éviction du moins récemment utilisé).
- Le cache est vidé à chaque application de configuration ou mise à jour des listes, VPN allumé ou non.
- Exigence : le cache doit aussi mémoriser les noms "autorisés" (absents de la table), qui sont l'immense majorité (voir défauts).

## 6. Serveurs DNS en amont

### 6.1 Origine des serveurs

Les serveurs DNS réels sont lus au moment de la construction du tunnel, dans cet ordre :

1. Réseau actif d'Android, s'il n'est pas lui-même un VPN.
2. Si le réseau actif est un VPN (le nôtre en cours de reconstruction, ou un autre) : premier réseau non VPN du même type (Wi-Fi ou données mobiles) qui publie des serveurs DNS.
3. S'il n'y a pas de réseau actif : premier réseau non VPN qui publie des serveurs DNS.

Il n'existe AUCUN réglage de DNS personnalisé dans l'app de référence.

### 6.2 Repli quand aucun serveur n'est trouvé

- Le tunnel n'est jamais construit sans résolveur (sinon plus aucune app ne résout de nom).
- Repli : 1.1.1.1 (Cloudflare). Si l'appareil n'a que de l'IPv6 (au moins une adresse IPv6 non locale et aucune adresse IPv4 sur ses réseaux non VPN, adresses de bouclage et lien local ignorées) : 2606:4700:4700::1111. Ce choix IPv6 se fait même si le réglage IPv6 est désactivé.
- Dès que le réseau publie ses vrais serveurs DNS (cas fréquent juste après un changement de réseau), le tunnel est reconstruit sur eux, sans attendre qu'une requête échoue. Pas de reconstruction si les "nouveaux" serveurs sont eux-mêmes uniquement le repli.

### 6.3 Règle IPv6

- Les serveurs IPv4 sont toujours retenus.
- Les serveurs IPv6 ne sont retenus que si le réglage "Activer la prise en charge d'IPv6" est actif (désactivé par défaut), OU si le réseau ne publie qu'un seul serveur DNS au total et qu'il est IPv6.
- Si tous les serveurs sont écartés par cette règle, le repli 6.2 s'applique.
- Un changement du réglage IPv6 n'est pris en compte qu'à la prochaine reconstruction du tunnel (à corriger, voir questions).

### 6.4 DNS chiffré (DoH)

- Le comportement actuel n'utilise PAS de DNS chiffré : tout part en DNS classique UDP vers les serveurs du réseau (ou le repli).
- L'ancien code contient un module DoH inactif (jamais branché, aucun réglage) qui visait https://cloudflare-dns.com/dns-query, amorcé par 1.1.1.1 et 1.0.0.1, IPv4 seulement, en POST. Ce module ne doit pas être repris tel quel (il ne renvoyait qu'une seule adresse, au mauvais type, et ne répondait rien en cas d'échec). Voir questions ouvertes.

## 7. Changements de réseau

### 7.1 Réseau qui porte le tunnel

- L'app suit séparément la présence du Wi-Fi (et s'il est "validé", c'est-à-dire avec accès Internet confirmé par Android) et la présence des données mobiles.
- Réseau visé, calqué sur le choix d'Android :
  - Wi-Fi et données présents : Wi-Fi s'il est validé, sinon données mobiles (Android garde les données comme réseau par défaut tant que le Wi-Fi n'est pas validé).
  - Wi-Fi seul : Wi-Fi. Données seules : données.
  - Aucun : pas de réseau.
- L'état initial est lu immédiatement au démarrage du service (avant les premiers événements réseau), pour construire le tunnel sur le bon réseau dès le départ.

### 7.2 Réactions

- **Passage à "aucun réseau"** : tunnel fermé, état "attente du réseau". Le service reste en vie.
- **Retour d'un réseau** alors que le tunnel était fermé pour cause de réseau : reconstruction immédiate (limitation remise à zéro, car c'est un vrai retour de connectivité). Pas de reconstruction si le tunnel avait été arrêté volontairement.
- **Changement de type de réseau** (données vers Wi-Fi validé, ou l'inverse) : reconstruction pour relire les DNS du nouveau réseau, sinon toutes les requêtes partent vers l'ancien réseau et échouent. La limitation n'est pas remise à zéro, pour amortir les bascules rapides.
- **Apparition ou disparition d'un réseau secondaire** qui ne change pas le réseau visé (ex. radio mobile qui clignote en économie d'énergie pendant que le Wi-Fi reste le réseau par défaut, constaté sur ColorOS) : AUCUNE reconstruction. L'icône VPN ne doit pas clignoter.
- **Changement des serveurs DNS sur le même réseau** (renouvellement DHCP, itinérance sur le même SSID, connexion à un portail captif) : reconstruction si, et seulement si, l'ensemble effectif des serveurs (après la règle IPv6 de 6.3 et le repli 6.2, ordre ignoré) change. Ignoré : réseau autre que celui qui porte le tunnel, liste momentanément vide pendant une transition, changement de routes ou de MTU seulement, changement IPv6 qui n'est pas utilisé. La première observation sert de référence (sauf cas du repli, 6.2).
- Toutes ces reconstructions passent par la limitation (10.3), sauf mention contraire.

### 7.3 Ethernet et autres types

- L'app de référence ne suit que Wi-Fi et données mobiles. Exigence : traiter Ethernet (boîtiers Android TV) et les autres types comme des réseaux à part entière, selon la même logique "réseau par défaut d'Android" (voir défauts).

## 8. Autres VPN, VPN permanent, verrouillage

### 8.1 Une autre app VPN prend la place

- Android retire le VPN à l'app. Si le consentement est perdu (cas d'une autre app VPN) : intention mise à "non", battement annulé, arrêt complet. L'app ne doit pas tenter de reprendre la place (hostile, et refusé par Android).
- Si Android retire le VPN alors que le consentement est conservé (gestionnaire de batterie constructeur, hibernation, vu sur ColorOS) : arrêt complet mais intention conservée à "oui", pour que le battement et l'ouverture de l'app le rétablissent.
- Dans les deux cas : état "arrêté" persisté et diffusé, pour que l'accueil, la tuile et la notification ne restent pas bloqués sur "actif".

### 8.2 VPN permanent (Always-on) d'Android

- Carte "VPN persistence" sur l'accueil téléphone (visible en mode VPN seulement) et bouton équivalent sur l'accueil TV. Elle ouvre une boîte de dialogue :
  - Ligne d'état : "Always-on VPN is enabled for AdAway. ✓" ou "Always-on VPN not set. AdAway's built-in persistence handles it."
  - Message : l'app gère déjà redémarrages, arrêts pour manque de mémoire et plantages ; épingler AdAway comme VPN permanent Android est la garantie la plus forte.
  - Sur TV seulement : commandes ADB de secours (l'écran VPN système est souvent caché sur TV).
  - Boutons "Open VPN settings" (ouvre l'écran VPN d'Android) et "Got it".
  - Si l'écran VPN d'Android est introuvable : message court "VPN settings are not available on this device." (téléphone, la boîte se ferme) ou "VPN settings hidden on this Android TV. Use the ADB commands shown." (TV, la boîte reste ouverte pour garder les commandes lisibles).
- Sur TV, un indicateur "Always-on VPN active" est visible quand AdAway est le VPN permanent ; il est rafraîchi au retour sur l'écran.
- Exigences :
  - Quand Android démarre l'app en tant que VPN permanent (au démarrage ou après un arrêt), l'app DOIT démarrer le tunnel et considérer l'intention comme "oui" (voir défauts : l'ancienne app ignorait ce démarrage).
  - La détection "AdAway est le VPN permanent" doit utiliser une méthode fiable (le service VPN peut interroger Android lui-même sur Android 10 et plus) plutôt que la lecture d'un réglage système souvent interdite.
  - Mode "Bloquer les connexions sans VPN" (verrouillage) : quand le tunnel est fermé (attente réseau, reconnexion, arrêt), plus rien ne passe sur l'appareil. Les reconstructions doivent donc être les plus courtes possible. Les apps exclues du VPN peuvent perdre tout accès en mode verrouillage (comportement Android) : à signaler à l'utilisateur.

## 9. Applications exclues

### 9.1 Applications système

Réglage "Exclude system applications" (liste à choix unique), défaut "None" :

| Choix (EN / FR) | Effet |
|---|---|
| None / Aucune | Aucune app système exclue. |
| All except browsers / Toutes, sauf les navigateurs | Toutes les apps système sont exclues, sauf les "navigateurs". |
| All / Toutes | Toutes les apps système sont exclues. |

- "Navigateurs" = toute app capable d'ouvrir le lien https://isabrowser.adaway.org/, plus toujours : com.google.android.webview, com.android.htmlviewer, com.google.android.backuptransport, com.google.android.gms, com.google.android.gsf (gardés dans le VPN car ils portent du trafic web pour d'autres apps).
- Une app système mise à jour reste une app système.
- Pourquoi exclure : certaines apps système (télécom, services du constructeur) fonctionnent mal derrière un VPN ; les exclure évite tout blocage chez elles.

### 9.2 Applications utilisateur

- Écran "Excluded applications" : liste de toutes les apps non système installées (sauf AdAway), avec icône, nom, nom de paquet et un interrupteur. Toucher la ligne bascule l'interrupteur.
- Tri par nom affiché. Exigence : tri alphabétique insensible à la casse et aux accents, chargement de la liste hors du fil d'interface, recherche souhaitable.
- Menu : "Select all" (tout exclure) et "Deselect all" (tout inclure).
- Chaque changement est enregistré immédiatement. Défaut : aucune app exclue.
- Les exclusions utilisateur ne s'appliquent qu'aux apps non système.
- Une app exclue mais désinstallée est ignorée sans erreur.
- Texte d'explication : "Configure which applications should not use the VPN so no connections will be blocked."
- Nécessite la permission de voir toutes les apps installées.

### 9.3 Prise en compte

- Changer le réglage système ou revenir de l'écran des apps utilisateur reconstruit le tunnel si le VPN tourne (les exclusions ne peuvent changer qu'à la construction). Exigence : ne reconstruire que si quelque chose a réellement changé, et que la reconstruction redémarre bien le tunnel (voir défauts).

## 10. Surveillance et reprise

### 10.1 Contrôle de l'interface du tunnel (toutes les 10 secondes)

- Pendant que le tunnel tourne, l'app vérifie toutes les 10 s qu'une interface tunnel (nommée "tunN") existe encore sur l'appareil.
- Le contrôle est suspendu tant que le tunnel n'est pas encore établi (attente de limitation), pour ne pas conclure à tort à sa disparition.
- On vérifie la présence de l'interface (pas son état "up", qui renvoie des erreurs à tort sur certains appareils et provoquait une boucle de redémarrage toutes les 10 s). La renumérotation (tun0 vers tun1) est tolérée. Si les interfaces ne peuvent pas être listées (erreur passagère), on suppose que tout va bien.
- Si l'interface a disparu et que l'intention est "oui" : le tunnel doit être réellement reconstruit (voir défauts). Si l'intention est "non" : rien.

### 10.2 Sonde de connexion (réglage "Monitor connection", désactivé par défaut)

- Quand le tunnel est resté totalement silencieux pendant un intervalle donné, l'app envoie un petit datagramme vide au serveur DNS réel (le dernier de la liste retenue), port 53. Si l'envoi échoue (réseau réellement perdu), le tunnel se reconnecte.
- Intervalle : 1 s au départ, multiplié par 4 à chaque période silencieuse : 1 s, 4 s, 16 s, 64 s, 256 s, 1024 s, puis plafonné à 4096 s (environ 68 min). Il est remis à 1 s à chaque nouveau démarrage du tunnel.
- Un tunnel silencieux n'est PAS un tunnel mort : l'absence de requêtes (téléphone au repos) ne doit jamais provoquer de reconnexion (l'ancienne version reconnectait toutes les quelques minutes pour rien).
- Réglage désactivé : aucune sonde, attente illimitée.
- Le réglage est lu au démarrage du tunnel.

### 10.3 Limitation des reconstructions

But : amortir les tempêtes de reconnexion, sans jamais pénaliser une action de l'utilisateur.

- Première tentative : immédiate.
- Ensuite, à chaque tentative : si elle arrive moins de "délai courant" après la précédente, le délai double (1 s, 2 s, 4 s ... jusqu'à 128 s maximum) et l'app attend le reste du délai ; si elle arrive après le délai, le délai est divisé par 4 (minimum 1 s), ou remis à 1 s si plus de 128 s se sont écoulées.
- Dans une boucle d'échecs continue, l'ancienne app attendait environ 2, 4, 8, 16, 32, 64 s puis alternait entre environ 43 et 86 s. Exigence pour la réécriture : un recul exponentiel simple et lisible (1 s doublé jusqu'à 128 s, revenant à 1 s après une période stable), documenté.
- Remise à zéro (tentative immédiate) : toute action explicite de l'utilisateur, démarrage au boot, retour de la connectivité après "aucun réseau".
- Pas de remise à zéro : changement de type de réseau, changement de DNS, erreurs.

### 10.4 Battement périodique (heartbeat)

- Tâche système périodique toutes les 15 minutes, uniquement quand un réseau est connecté, programmée à chaque démarrage réussi du VPN et annulée à chaque arrêt volontaire.
- À chaque passage : si l'intention est "oui" et que le service n'existe pas, il est redémarré.
- Exigence : le battement doit aussi détecter un service vivant mais sans tunnel (voir défauts).

### 10.5 Ouverture de l'app

- Un arrêt forcé par le gestionnaire de batterie d'un constructeur bloque tous les moyens de reprise de l'app (redémarrage automatique du service, tâches périodiques, diffusions) jusqu'à ce que l'utilisateur rouvre l'app.
- À l'ouverture de l'accueil (téléphone et TV) : si le mode est VPN, que l'intention est "oui" et que le service n'existe pas, le VPN est redémarré.

### 10.6 Résurrection par Android

- Le service demande à être relancé par Android s'il est tué (manque de mémoire, plantage).
- Lors d'une relance automatique par Android : si l'intention est "non", la relance est refusée et le service s'arrête aussitôt ; sinon le VPN est redémarré.

### 10.7 Refus du passage au premier plan

- Si Android refuse que le service passe au premier plan (cas d'un processus relancé par un gestionnaire de batterie), l'app s'arrête proprement : état "arrêté" persisté et diffusé, pour que l'interface ne reste pas bloquée sur "pause". Le tunnel ne doit pas être lancé dans ce cas (voir défauts). L'intention reste "oui" pour que le battement réessaie.

### 10.8 Erreur inattendue

- Une erreur inconnue dans le tunnel ne doit pas être réessayée en boucle. Le VPN doit s'arrêter réellement et proprement (service compris), avec un état "arrêté" cohérent partout, l'intention restant "oui" pour que le battement ou l'ouverture de l'app le relance (voir défauts).

## 11. Démarrage au boot

- Réglage "Enable at startup", ACTIVÉ par défaut.
- À la fin du démarrage de l'appareil, si le mode est VPN et que le réglage est actif : l'intention est mise à "oui" (le réglage est considéré comme une intention explicite, même si l'utilisateur avait mis en pause avant d'éteindre), puis le VPN est démarré.
- Si le consentement VPN manque : l'app ne peut pas afficher la demande depuis l'arrière-plan. Exigence : ne pas lancer de tunnel en boucle, afficher une notification "autorisation requise" qui ouvre l'app (voir 3).
- Si le réglage est inactif : rien au démarrage (un VPN permanent Android reste prioritaire, 8.2).

## 12. Persistance face aux tueurs de batterie

- Assistant de premier lancement (zone accueil) : étape "Stay protected in the background" qui demande l'exemption d'optimisation de batterie Android (bouton "Allow", "Allowed ✓" une fois accordée, état relu au retour), puis étape de conseils propres à la marque détectée (Samsung, Xiaomi/Redmi/POCO, Oppo/OnePlus/Realme, Huawei/Honor, vivo/iQOO, autre) avec un bouton "Open app info". Ces étapes sont informatives et ne bloquent pas l'assistant.
- Mécanismes de l'app (décrits plus haut) : service au premier plan avec notification, relance automatique par Android, battement de 15 min, reprise à l'ouverture de l'app, conseil d'épingler le VPN permanent.
- Sur Android 12 et plus, Android peut interdire de démarrer un service au premier plan depuis l'arrière-plan (battement par exemple) si l'app n'est pas exemptée d'optimisation de batterie. Exigence : intercepter ce refus sans planter, et le signaler (état ou notification) plutôt que d'échouer en silence.

## 13. Notification

### 13.1 Canal

- Canal "VPN" (FR "RPV/VPN"), description "VPN running notifications" (FR "Notifications de l'exécution du RPV/VPN"), importance basse (pas de son).

### 13.2 Contenu

- Titre : "Ad-blocker VPN %1$s" (FR "RPV/VPN bloqueur de publicités %1$s"), où %1$s est l'état en minuscules (13.3). Pas de texte secondaire.
- Petite icône : logo de l'app, couleur de marque, notification colorisée.
- Toucher la notification ouvre l'accueil (nouvelle tâche).
- État "actif" : action "Pause" (icône pause), notification permanente.
- État "arrêté" : action "Resume" / "Reprendre", notification qu'on peut balayer.
- Autres états : pas d'action.

### 13.3 Libellés d'état

| État | EN | FR |
|---|---|---|
| Démarrage | starting | démarrage |
| Actif | active | activé |
| Arrêt en cours | stopping | arrêt |
| Attente réseau | wait for network | en attente du réseau |
| Reconnexion | reconnecting | reconnexion |
| Erreur de reconnexion | reconnecting error | erreur de reconnexion |
| Arrêté | stopped | arrêté |

(Un libellé "paused" / "en pause" existe mais n'est jamais utilisé.)

### 13.4 Règles d'affichage

- Pendant "démarrage" et "actif" : notification de premier plan obligatoire (exigence Android). La notification "arrêté" éventuelle est retirée.
- Exigence pour la réécriture : UNE seule notification qui reflète toujours l'état courant (voir défauts : l'ancienne app en affichait deux). Après un arrêt volontaire, une notification "arrêté" avec "Reprendre" peut rester, si la permission de notifier est accordée.
- Si la permission de notification est refusée (Android 13+), le service fonctionne quand même ; seules les notifications hors premier plan sont omises.

## 14. États affichés à l'utilisateur

- **Notification** : voir 13.
- **Accueil téléphone** : en-tête et barre d'état en couleur de marque quand le blocage est actif, en gris sinon ; bouton flottant avec icône pause (actif) ou logo (inactif). Pendant une action, un indicateur de progression ; après activation, message "VPN configuration successfully updated" (FR "La configuration du RPV/VPN a été mise à jour avec succès"). Les erreurs ouvrent une boîte avec "Help" et "Close".
- **Accueil TV** : "Ad-blocking: enabled" / "Ad-blocking: disabled", indicateur VPN permanent (8.2).
- **Tuile des réglages rapides** : active ou inactive selon l'état ; un toucher bascule ; les touchers pendant une bascule en cours sont ignorés ; après chaque bascule (succès ou échec) la tuile est resynchronisée sur l'état réel.
- "Actif" côté interface = l'état réel est démarré. Les états intermédiaires (démarrage, reconnexion, attente réseau) ne doivent pas faire passer l'interface à "inactif". Exigence : l'accueil et la tuile doivent pouvoir montrer distinctement "attente réseau" et "autorisation requise" (voir questions).
- Toutes les surfaces (notification, accueil, tuile, icône clé VPN d'Android) doivent rester cohérentes, y compris après un arrêt forcé externe.

## 15. Journal des requêtes DNS (mode VPN)

- Écran "DNS requests". L'enregistrement est désactivé par défaut et se déclenche par un bouton (icône enregistrement / pause).
- En mode VPN, chaque nom demandé par une app filtrée est enregistré (y compris les noms bloqués), en minuscules, sans doublon, sans heure ni compteur. Les apps exclues n'apparaissent pas.
- Mémoire seulement : au plus 8192 noms distincts, le plus ancien est retiré au-delà. Le journal et l'état "enregistrement" sont perdus si le processus s'arrête.
- Rien n'est enregistré quand le VPN est arrêté. Sur TV, le bouton est désactivé avec le libellé "Start Recording (VPN not active)" et l'enregistrement s'arrête tout seul si le VPN s'arrête ; rafraîchissement automatique chaque seconde pendant l'enregistrement. Sur téléphone, rafraîchissement par balayage vers le bas et au retour sur l'écran.
- Tri : par domaine de premier niveau (défaut) ou alphabétique ; bouton pour basculer, message court indiquant le tri choisi. Bouton pour vider.
- Pour chaque nom : bloquer, autoriser, rediriger (adresse IP validée), retirer la règle utilisateur, ouvrir dans le navigateur, copier. Une invite propose ensuite d'appliquer la configuration.
- Le détail de cet écran relève de la zone "journal DNS" ; seules les règles propres au VPN sont listées ici.

## 16. Journal de diagnostic

- Réglage "Record diagnostic log", désactivé par défaut. Prise d'effet immédiate, sans redémarrage.
- Enregistre sur disque les événements techniques de niveau information et plus (démarrage, arrêt, changements de réseau, avertissements, erreurs), pas le détail par requête.
- Confidentialité : aucun nom de domaine résolu ne doit y figurer. Les lignes d'échec qui citent un nom sont gardées avec le nom remplacé par "(host hidden)".
- Format d'une ligne : "MM-jj HH:mm:ss" + lettre de niveau (I, W, E, A, D) + "/" + étiquette + ": " + message ; trace d'erreur ajoutée si présente.
- Taille : fichier actif de 100 Ko au plus, puis rotation dans un unique fichier de sauvegarde (200 Ko au total au plus). L'écriture ne bloque jamais l'app ; une erreur d'écriture est ignorée.
- "Diagnostic log" : affiche les 20 000 derniers caractères (précédés de "…" si tronqué) dans une zone défilante de hauteur fixe, boutons "Share", "Copy", "OK" (seulement "OK" si vide, avec le message d'invite). Partage en texte brut avec le titre "Share diagnostic log". Copie : message "Diagnostic log copied to clipboard.".
- "Clear diagnostic log" : supprime tout, message "Diagnostic log cleared.".

## 17. Préférences VPN

| Réglage (EN / FR) | Type | Défaut | Effet | Prise en compte |
|---|---|---|---|---|
| Enable at startup / Activer au démarrage | interrupteur | Activé | Démarre le VPN au boot et met l'intention à "oui" | Boot suivant |
| Monitor connection / Surveiller la connexion | interrupteur | Désactivé | Sonde de connexion (10.2) | Prochain démarrage du tunnel |
| Exclude system applications / Exclure les applications système | liste | None | 9.1 | Reconstruction immédiate si VPN actif |
| Exclude user applications / Exclure des applications utilisateur | écran | aucune | 9.2 | Reconstruction au retour si VPN actif |
| Reset VPN / Réinitialiser le RPV/VPN | action | - | Reconstruit le tunnel de zéro (relit les DNS) | Immédiat |
| Record diagnostic log / Enregistrer le journal de diagnostic | interrupteur | Désactivé | 16 | Immédiat |
| Diagnostic log / Journal de diagnostic | action | - | 16 | - |
| Clear diagnostic log / Effacer le journal de diagnostic | action | - | 16 | - |
| Enable IPv6 support / Activer la prise en charge d'IPv6 | interrupteur (section commune) | Désactivé | 6.3 | Prochaine reconstruction |

Réglages internes persistés (non visibles) : intention utilisateur, dernier état du service.

"Reset VPN" : si le VPN tourne, reconstruction et message "VPN connection rebuilt." ; sinon message "VPN is not running." sans rien faire. Description : "Rebuild the VPN connection from scratch. Use this if the VPN stops working (no internet access or DNS errors)."

Section "VPN based ad blocker" grisée si le mode actif est root.

## 18. Textes et messages (anglais d'abord, français ensuite)

### 18.1 Préférences

- "VPN based ad blocker" / "Bloqueur de publicités fondé sur un RPV/VPN"
- "Periodically check the network state to restart VPN on disconnection." / "Vérifier régulièrement l'état du réseau pour relancer le RPV/VPN après une déconnexion."
- "Configure which applications should not use the VPN so no connections will be blocked." / "Configurer les applis qui ne devraient pas utiliser le RPV/VPN, afin qu'aucune connexion ne soit bloquée."
- "Excluded applications" / "Applications exclues" ; "Select all" / "Les sélectionner toutes" ; "Deselect all" / "Les dessélectionner toutes" ; "Application icon" / "Icône de l'application"
- "Reset VPN" / "Réinitialiser le RPV/VPN" ; "VPN connection rebuilt." / "Connexion RPV/VPN reconstruite." ; "VPN is not running." / "Le RPV/VPN n'est pas actif."
- "Turn on before reproducing a problem, then export it below. May slightly slow down older devices. Records only technical VPN events, never browsing history or personal data." / "Activez-la avant de reproduire un problème, puis exportez-la ci-dessous. Peut légèrement ralentir les appareils anciens. N'enregistre que des événements techniques du RPV/VPN, jamais de données personnelles."
- "View, copy or share the recent errors and VPN events. Useful when reporting a problem." / "Consulter, copier ou partager les erreurs et événements RPV/VPN récents. Utile pour signaler un problème."
- "No events have been recorded yet. Turn on \"Record diagnostic log\" above, then try to reproduce the problem." / "Aucun événement n'a encore été enregistré. Activez « Enregistrer le journal de diagnostic » ci-dessus, puis essayez de reproduire le problème."
- "Share" / "Partager" ; "Share diagnostic log" / "Partager le journal de diagnostic" ; "Copy" / "Copier" ; "Diagnostic log copied to clipboard." / "Journal de diagnostic copié dans le presse-papiers." ; "Delete the recorded events. Do this before reproducing a problem to capture a clean trace." / "Supprimer les événements enregistrés. À faire avant de reproduire un problème pour capturer une trace propre." ; "Diagnostic log cleared." / "Journal de diagnostic effacé."

### 18.2 Notification

Voir 13.2 et 13.3. "Pause" / "Pause" ; "Resume" / "Reprendre".

### 18.3 Erreurs

- "Failed to enable VPN ad blocking." / "Échec d'activation du blocage des publicités par RPV/VPN."
- "Please check your VPN settings to authorize the application VPN to start." / "Vérifiez les paramètres de votre RPV/VPN pour autoriser le démarrage du RPV/VPN de l'application."
- "Failed to disable VPN ad blocking." / "Échec de désactivation du blocage des publicités par RPV/VPN." ; "Please check your VPN settings to disable it manually." / "Vérifiez les paramètres de votre RPV/VPN pour le désactiver manuellement." (jamais déclenché dans l'ancienne app)

### 18.4 Persistance

- "VPN persistence" / "Persistance VPN" ; "Keep ad blocking running in the background" / "Maintenir le blocage actif en arrière-plan"
- "AdAway already handles reboots, low-memory kills and crashes on its own, so no extra setup is needed in most cases.\n\nIf the VPN ever stops unexpectedly, pin AdAway as the Android system Always-on VPN for the strongest guarantee." / "AdAway gère déjà les redémarrages, les kills système et les crashs, donc normalement aucune configuration n'est nécessaire.\n\nSi le VPN se coupe de façon inattendue, vous pouvez activer Always-on VPN au niveau Android pour renforcer la persistance."
- "Always-on VPN is enabled for AdAway. ✓" / "Always-on VPN activé pour AdAway. ✓" ; "Always-on VPN not set. AdAway's built-in persistence handles it." / "Always-on VPN non configuré. AdAway gère via sa persistance interne."
- "Open VPN settings" / "Ouvrir les réglages VPN" ; "Got it" / "Compris" ; "Always-on VPN active" / "Always-on VPN actif"
- "VPN settings are not available on this device." / "Les réglages VPN ne sont pas accessibles sur cet appareil." ; "VPN settings hidden on this Android TV. Use the ADB commands shown." / "Réglages VPN cachés sur cet Android TV. Utilisez les commandes ADB affichées."

### 18.5 Assistant

- "Always-on VPN enabled" / "Le RPV/VPN permanent est activé"
- "AdAway detected that another VPN has the \"Always-on VPN\" setting active.\n\nThis prevents AdAway from requesting VPN access. Please disable \"Always-on VPN\" on the correspondent VPN and try again." / version FR équivalente.
- "AdAway failed to request VPN access.\n\nThis might be caused because another VPN has the \"Always-on VPN\" setting active. Please, check if any VPN has this setting enabled and disable it before trying again." / version FR équivalente.
- "Open \"VPN\" Settings" / "Ouvrir les paramètres « RPV/VPN »"

## 19. Situations délicates et comportement attendu

| Situation | Comportement attendu |
|---|---|
| Réseau perdu complètement | Tunnel fermé, état "attente du réseau", service vivant, aucune boucle de tentatives. |
| Réseau revenu | Reconstruction immédiate sur les DNS du nouveau réseau, limitation remise à zéro. |
| Passage données vers Wi-Fi | Rester sur les données tant que le Wi-Fi n'est pas validé, puis reconstruire une seule fois. |
| Wi-Fi sans Internet (objet connecté, portail captif) avec données actives | Rester sur les données (comme Android). |
| Radio mobile qui clignote alors que le Wi-Fi reste par défaut | Aucune reconstruction. |
| Bascules rapides et répétées | Reconstructions amorties par la limitation (jusqu'à 128 s entre tentatives). |
| DNS du réseau qui change sans changement de réseau | Reconstruction uniquement si l'ensemble effectif change. |
| Réseau qui ne publie pas encore de DNS au moment de la construction | Repli 1.1.1.1 (ou 2606:4700:4700::1111 si IPv6 seul), puis reconstruction automatique dès que les vrais DNS arrivent. |
| Réseau IPv6 seul | Repli IPv6 joignable, jamais un repli IPv4 sans route. |
| Un seul serveur DNS, en IPv6, réglage IPv6 désactivé | Utilisé quand même (sinon plus aucune résolution). |
| Serveur DNS qui ne répond pas | Requête oubliée après 10 s, l'app cliente réessaie ; le tunnel continue. |
| Réseau injoignable à l'envoi d'une requête | Reconnexion du tunnel (avec limitation). |
| Trop de requêtes en attente | 1024 au plus, la plus ancienne est abandonnée. |
| Réponse DNS de plus de 1 Ko (DNSSEC, nombreuses adresses, longs TXT) | Relayée en entier jusqu'à 4096 octets. |
| Réponse provenant d'une autre adresse que le serveur interrogé | Ignorée. |
| Paquet malformé envoyé par une app | Abandonné, le tunnel continue. |
| Datagramme vide (Firefox) | Relayé vide vers le vrai serveur. |
| DNS en TCP | Non pris en charge (abandonné) : limite connue. |
| Tunnel silencieux longtemps (téléphone au repos) | Aucune reconnexion. |
| Interface tunnel disparue | Reconstruction réelle si intention "oui". |
| Pause puis événement réseau immédiat | Le VPN reste arrêté. |
| Reconstruction pendant qu'un ancien tunnel finit de se fermer | Jamais deux tunnels actifs ensemble ; l'ancien se retire sans annoncer d'état. |
| Autre app VPN activée | Arrêt, intention "non", pas de reprise de force. |
| Retrait par Android sans perte du consentement (constructeur) | Arrêt, intention conservée, reprise par battement ou ouverture de l'app. |
| Arrêt forcé par un gestionnaire de batterie | Reprise à la prochaine ouverture de l'app (seul moment possible) ; conseiller le VPN permanent. |
| Processus tué (mémoire, plantage) | Relance par Android, refusée si intention "non". |
| Service vivant mais état persisté faux, ou l'inverse | État corrigé à la lecture ; interface cohérente. |
| Passage au premier plan refusé | Arrêt propre, état "arrêté", pas de tunnel lancé. |
| Erreur inattendue dans le tunnel | Arrêt réel et cohérent, pas de boucle. |
| Démarrage d'Android avec "Activer au démarrage" | VPN démarré (consentement vérifié). |
| Démarrage par Android en tant que VPN permanent | VPN démarré, intention "oui". |
| Mode verrouillage Android actif | Fenêtres sans tunnel les plus courtes possible ; avertir que les apps exclues perdent l'accès. |
| Changement des apps exclues | Reconstruction effective du tunnel. |
| Mise à jour des listes, VPN actif | Pas de reconstruction ; cache vidé, nouvelles règles prises à la volée. |
| Mise à jour des listes, intention "non" | VPN laissé arrêté. |
| Ethernet (TV) | Traité comme un réseau à part entière (voir défauts). |

## 20. Défauts ou bizarreries constatés (à ne pas reproduire)

- **"Reset VPN" et changement des apps exclues arrêtent le VPN au lieu de le redémarrer** : l'arrêt est envoyé de façon différée, puis le démarrage voit le VPN encore "actif" et ne fait rien ; l'arrêt s'exécute ensuite. Le message "VPN connection rebuilt." s'affiche pourtant, et le battement périodique est annulé. (Constaté à la lecture du code, à confirmer sur appareil.)
- **Démarrage par le VPN permanent ignoré** : quand Android lance l'app en tant que VPN permanent, la demande ne porte pas la commande attendue et l'app ne démarre pas le tunnel. Le conseil "épinglez AdAway en VPN permanent" perd alors son intérêt après un arrêt.
- **Surveillance de l'interface inopérante** : quand l'interface tunnel disparaît, la surveillance demande un démarrage qui ne fait rien (le service est vivant et marqué actif), puis se coupe pour le reste de la session.
- **Erreur inattendue laisse un service fantôme** : le tunnel est fermé et l'interface passe à "arrêté", mais le service reste vivant au premier plan avec une notification "actif" ; le battement ne le relance pas puisqu'il est "vivant".
- **Refus du premier plan mal géré** : en cas de refus, l'app s'arrête mais lance quand même le tunnel juste après.
- **Démarrage sans consentement en boucle** : tuile, notification, boot ou battement lancent le service sans vérifier le consentement ; la construction du tunnel échoue et l'app réessaie indéfiniment avec "erreur de reconnexion".
- **Demande de consentement en boucle sur l'accueil** : la vérification est faite à chaque retour sur l'accueil ; un refus fait réapparaître la demande aussitôt. Et un consentement accordé ne démarre pas le VPN (résultat ignoré sur téléphone).
- **Boot : demande de consentement depuis l'arrière-plan** : Android 10+ bloque l'affichage ; rien ne prévient l'utilisateur.
- **"Activer au démarrage" écrase la pause** : un VPN mis en pause redémarre au reboot (défaut activé). Choix discutable, à trancher.
- **Deux notifications** : les états hors "démarrage/actif" s'affichent dans une seconde notification pendant que celle du premier plan garde l'ancien état ("actif" à côté de "en attente du réseau").
- **"Attente réseau" peut finir affiché "arrêté"** : en fermant le tunnel pour attendre le réseau, la fin du travail du tunnel annonce "arrêté" ; l'accueil et la tuile passent à inactif alors que le service attend. Un toucher sur la tuile ne fait alors rien. (Probable, dépend du réveil de l'attente système.)
- **Ethernet ignoré** : seuls Wi-Fi et données sont suivis. Sur une TV en Ethernet, aucun changement de réseau n'est géré ; et si un Wi-Fi apparaît puis disparaît, le tunnel est fermé ("attente réseau") alors que l'Ethernet fonctionne.
- **Réponse de redirection au mauvais type** : une adresse IPv4 de redirection est renvoyée en A même pour une question AAAA, HTTPS ou MX ; le drapeau "récursion désirée" est retiré de la réponse.
- **Cache inutile pour les noms autorisés** : le cache ne garde pas les noms absents de la table, donc chaque requête autorisée (la majorité) interroge la base.
- **Délai de 10 s appliqué tardivement** : les requêtes expirées ne sont nettoyées qu'à l'arrivée d'une nouvelle requête ; sans trafic, des connexions restent ouvertes.
- **Sonde qui ne se réinitialise jamais en cours de session** : après quelques heures, l'intervalle reste à 68 min.
- **Envoi vide (Firefox) qui provoque une reconnexion** : n'importe quelle erreur sur cet envoi déclenche une reconnexion, au lieu des seules erreurs "réseau injoignable".
- **Commandes ADB TV probablement fausses** : elles écrivent le réglage dans l'espace "global" alors qu'Android le lit dans l'espace "secure", et ne gèrent pas le mode verrouillage.
- **Détection du VPN permanent peu fiable** : la lecture du réglage système est refusée sur Android récent ; l'app conclut alors "non configuré" même quand c'est faux.
- **Module DoH mort** : présent mais jamais utilisé, vise Cloudflare en dur, renvoie une seule adresse au mauvais type et ne répond rien en cas d'échec.
- **Repli DNS vers Cloudflare sans prévenir** : les requêtes partent vers un tiers sans que l'utilisateur le sache.
- **Liste des apps exclues** : chargée sur le fil d'interface (lent), tri sensible à la casse (majuscules avant minuscules), exclusion de l'app elle-même faite sur un critère fragile (nom de classe et non nom de paquet).
- **Retour de l'écran d'exclusion** : reconstruction demandée même si rien n'a changé.
- **Changement du réglage IPv6 ou "Surveiller la connexion"** : aucun effet avant la prochaine reconstruction, sans le dire.
- **Commande externe qui suit le mode actif** : "Reprendre" depuis une vieille notification VPN en mode root applique le mode root.
- **Tunnel construit même sans aucun réseau au démarrage** : il démarre sur le repli puis est reconstruit dès qu'un réseau arrive (inoffensif mais inutile).
- **Plage IPv4 virtuelle sans contrôle de conflit** : 192.0.2.0/24 est utilisée même si le réseau local l'utilise (rare) ; l'adresse IPv6 du tunnel est l'adresse "zéro" du préfixe.
- **Libellés inutilisés** : "paused", "Running/Stopped", "Enable VPN/Disable VPN", "Start or stop VPN to filter requests." existent sans usage.

## 21. Questions ouvertes

1. DNS en amont : proposer un DNS personnalisé (IPv4/IPv6) et/ou un DNS chiffré (DoH, DoT) ? Si oui : quels fournisseurs prédéfinis, saisie libre, repli vers le DNS du réseau en cas d'échec, et comportement sur portail captif.
2. Repli quand le réseau ne publie aucun DNS : garder Cloudflare (1.1.1.1 / 2606:4700:4700::1111), le rendre configurable, ou l'annoncer à l'utilisateur ?
3. "Activer au démarrage" doit-il respecter une pause manuelle faite avant l'extinction (ne redémarrer que si l'intention était "oui") ?
4. Faut-il prendre en charge le DNS en TCP (réponses tronquées, grosses réponses) vers les adresses virtuelles ?
5. Faut-il gérer le "DNS privé" d'Android en mode strict (qui contourne le filtre) : détection et avertissement ?
6. Exclusions : autoriser l'exclusion individuelle d'apps système ? Ajouter une recherche ? Proposer une liste par défaut d'apps connues pour mal supporter un VPN ?
7. Changement du réglage IPv6 ou de la sonde : reconstruire immédiatement le tunnel si le VPN tourne ?
8. Garder la sonde de connexion ("Monitor connection") alors que les autres mécanismes détectent déjà les pannes ? Si oui, quel calendrier exact ?
9. États à montrer sur l'accueil et la tuile : distinguer "attente réseau", "reconnexion", "autorisation requise" ? Le texte de la notification doit-il donner un détail (réseau utilisé, nombre de requêtes bloquées) ?
10. Type de service au premier plan exigé par les versions récentes d'Android (cible 34 et plus) : lequel déclarer et avec quelle justification ?
11. Mode verrouillage Android ("Bloquer les connexions sans VPN") : avertir que les apps exclues perdent tout accès ? Le détecter ?
12. Journal DNS : le conserver après un redémarrage de l'app ? Ajouter l'heure, le nombre de requêtes, le résultat réel (bloqué ou non au moment de la requête) ?
13. Commande externe START/STOP : la conserver, et doit-elle ne viser que le mode VPN ?
14. Migration depuis l'ancienne app (même identifiant) : reprendre l'intention, l'état, les exclusions et les réglages existants ?
15. Ethernet et autres types de réseau (USB, Bluetooth) : quelle priorité entre eux et le Wi-Fi ?
16. Réponse pour un nom bloqué : garder NODATA (SOA, TTL 5 s) ou passer à NXDOMAIN / 0.0.0.0 ? (NODATA évite que certaines apps réessaient en boucle, à confirmer.)

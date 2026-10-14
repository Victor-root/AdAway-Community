# 07. Pièges à éviter (tirés de l'historique du projet)

## Sources utilisées

- Sur la branche `main` : `CHANGELOG.md` (toutes les versions, de 1.29 à 6.6.1-c), `README.md`, `RELEASING.md`, `CONTRIBUTING.md`, `docs` (ne contient qu'un badge, rien d'utile).
- Historique git complet de `main` : les 149 commits du fork communautaire (depuis le 2026-05-03) et les 600 derniers commits (hors robots de traduction), plus les correctifs anciens importants retrouvés par mot-clé.
- Les références sont des hash de commit (8 caractères) ou des versions du changelog (ex. `6.6.0-c`, `5.0.3`).

Format de chaque point :
- **Vu par l'utilisateur** : ce que la personne constate.
- **Cause** : la vraie raison trouvée dans le commit.
- **Exigence** : le comportement que la nouvelle app doit garantir.
- **Réf** : commit ou version.

Quand la cause n'est pas écrite dans la source, c'est indiqué "cause non documentée".

---

## 1. Premier lancement et assistant d'accueil

### 1.1 Blocage activé sur une base vide (course au premier lancement)
- **Vu par l'utilisateur** : nouvelle installation, le blocage dit "activé", mais rien n'est bloqué, aucune erreur.
- **Cause** : les sources par défaut sont insérées en tâche de fond à la première ouverture de la base. La première synchro lisait la liste des sources avant la fin de cette insertion, trouvait une liste vide, annonçait un succès et activait le blocage sans aucune règle.
- **Exigence** : la toute première synchro doit attendre que les sources par défaut soient réellement enregistrées. Une synchro qui ne trouve aucune source à traiter ne doit jamais être présentée comme un succès qui active le blocage.
- **Réf** : 43724cb2, `6.6.0-c`.

### 1.2 Première activation qui démarre le VPN avant d'avoir téléchargé quoi que ce soit (TV)
- **Vu par l'utilisateur** : sur TV, juste après l'accord VPN, "activé" et "configuration VPN mise à jour", avec 0 bloqué, 0 source à jour. Le blocage est actif et ne fait rien.
- **Cause** : la TV appelait la bascule simple (qui démarre le VPN avec la base locale, vide) au lieu du chemin "synchroniser puis activer" utilisé sur mobile.
- **Exigence** : toute première activation, sur tous les appareils, synchronise d'abord les sources et ne démarre pas le blocage si cette synchro échoue. La bascule ordinaire ensuite n'a pas besoin de resynchroniser.
- **Réf** : f6f62b37, `6.6.0-c`.

### 1.3 L'écran d'accueil annonce "synchro terminée" alors qu'elle tourne encore
- **Vu par l'utilisateur** : coche verte en une seconde avec "trois sources et une poignée d'hôtes".
- **Cause** : l'écran regardait "le blocage est-il actif ?". En root, cet état est lu dans la première ligne de `/system/etc/hosts`, un fichier système qui survit à une désinstallation ou un effacement des données. Après une réinstallation, l'ancien en-tête faisait croire que tout était déjà appliqué.
- **Exigence** : l'écran d'accueil suit la fin de l'opération qu'il a lui-même lancée (sources récupérées puis blocage appliqué), pas un état global. L'état "appliqué" en root ne doit pas être déduit d'un reste laissé par une installation précédente.
- **Réf** : e26304a7, `6.6.1-c`.

### 1.4 Utilisateur piégé sur l'étape de synchro
- **Vu par l'utilisateur** : le bouton "Suivant" est caché tant que la synchro n'est pas finie, sans aucun moyen de sortir.
- **Cause** : choix d'interface, alors que la synchro continue en arrière-plan quel que soit l'écran affiché.
- **Exigence** : on peut toujours quitter l'étape de synchro. Si elle n'est pas finie, on demande confirmation ("Continuer quand même" / "Attendre"). Les étapes qui exigent vraiment une action (choix de la méthode, batterie) peuvent garder leur blocage.
- **Réf** : b67caed8, `6.6.1-c`.

### 1.5 Pas d'assistant sur TV, méthode choisie en silence
- **Vu par l'utilisateur** : sur TV, on arrivait directement à l'accueil, VPN imposé, base vide, aucun choix root.
- **Cause** : les écrans d'accueil du téléphone ne sont pas utilisables à la télécommande, donc la TV les sautait et forçait le VPN sans vérifier le root.
- **Exigence** : la TV a son propre assistant, utilisable au D-pad, avec le même choix root ou VPN que le téléphone. La fin de l'assistant est mémorisée par un indicateur dédié, pas déduite de la méthode choisie (qui est fixée dès l'ouverture de l'assistant).
- **Réf** : a2f6c5d4, b70a73b5, `6.6.0-c`.

### 1.6 Un écran TV exporté pouvait choisir la méthode sur un téléphone
- **Vu par l'utilisateur** : rien de visible, faille.
- **Cause** : l'écran d'accueil TV fixait la méthode à "VPN" si elle n'était pas définie. Comme il est accessible aux autres apps, n'importe quelle app pouvait l'ouvrir sur un téléphone et décider à la place de l'utilisateur.
- **Exigence** : le choix par défaut de la méthode ne se fait que sur un vrai téléviseur. Aucun point d'entrée accessible aux autres apps ne doit pouvoir choisir la méthode à la place de l'utilisateur.
- **Réf** : 0d9fc329.

### 1.7 État de la méthode incohérent quand le test root ou VPN échoue
- **Vu par l'utilisateur** : l'assistant reste dans un état indéterminé.
- **Cause** : en cas d'échec du test root ou VPN, la méthode n'était pas remise à "non définie". Le test root démarrait aussi trop tard (shell pas encore prêt).
- **Exigence** : si le test d'une méthode échoue ou est refusé, la méthode reste "non définie" et l'utilisateur peut en choisir une autre. Le test root doit être lancé assez tôt pour donner une réponse nette.
- **Réf** : 40cb39bc, e8731b25, `6.0.2`.

### 1.8 Gel ("L'application ne répond pas") sur l'écran de choix, avec une souris
- **Vu par l'utilisateur** : boucle de messages ANR quand une souris est branchée (scrcpy ou Bluetooth).
- **Cause** : l'attente du shell root se faisait sur le fil principal et bloquait la distribution des événements d'entrée (délai de 5 s).
- **Exigence** : aucune vérification root, ni aucune attente système, ne bloque le fil de l'interface. La carte "root" reste non cliquable pendant la vérification.
- **Réf** : ca79d7bd, ba52295b (entrée `6.5.5-c` du changelog, fusionnée ensuite dans `6.6.0-c`).

### 1.9 Autre VPN en "Always-on" qui empêche l'accord VPN
- **Vu par l'utilisateur** : la demande d'accès VPN échoue sans explication.
- **Cause** : un autre VPN réglé en "Always-on" empêche AdAway d'obtenir le VPN. Sur certaines versions, le réglage `always_on_vpn_app` n'est même pas lisible (exception de sécurité).
- **Exigence** : si l'accord VPN échoue, l'app explique la cause probable (autre VPN en Always-on) et propose d'ouvrir les réglages VPN. Une lecture interdite du réglage ne doit jamais faire planter l'app.
- **Réf** : 647b9498, `6.1.5`.

### 1.10 Double fenêtre d'accord VPN sur TV
- **Vu par l'utilisateur** : sur Shield TV, il faut cliquer "OK" deux fois.
- **Cause** : l'application du thème au premier lancement recrée l'écran, et la vérification VPN était relancée à chaque recréation.
- **Exigence** : une demande système (accord VPN, permission) n'est déclenchée qu'une seule fois par lancement réel, jamais à chaque recréation d'écran (rotation, changement de thème).
- **Réf** : 1e9d1d56, `6.5.1-c`.

### 1.11 Économiseurs de batterie des constructeurs
- **Vu par l'utilisateur** : le VPN meurt en arrière-plan malgré l'exemption Doze.
- **Cause** : Samsung, Xiaomi, ColorOS, Huawei, vivo ont leur propre gestionnaire de batterie qui tue les VPN en arrière-plan.
- **Exigence** : l'accueil guide l'utilisateur vers l'exemption Doze d'Android et, sur ces marques, vers les écrans du gestionnaire constructeur.
- **Réf** : 47a3a848, `6.6.0-c`.

---

## 2. Intention de l'utilisateur et état réel du VPN

### 2.1 Le VPN se rallume tout seul après un arrêt manuel
- **Vu par l'utilisateur** : on coupe le VPN, il revient tout seul (problèmes amont #4022, #4234).
- **Cause** : plusieurs chemins d'arrière-plan (mise à jour périodique des sources, bouton "Appliquer", synchro, moniteur de connexion, redémarrage automatique du service par Android) appelaient la même fonction que l'utilisateur et redémarraient le VPN.
- **Exigence** : l'app garde deux informations séparées : ce que l'utilisateur veut (on/off, persistant) et l'état réel du service. Aucun chemin d'arrière-plan ne peut démarrer le blocage si l'utilisateur l'a coupé. Seules les actions explicites (bouton, tuile, notification, "activer au démarrage") changent l'intention. Cette règle doit être testable sans Android.
- **Réf** : 6afae8ae, README (section "Mobile / VPN stability"), PR amont #4255.

### 2.2 Le redémarrage automatique par Android ressuscite un VPN coupé
- **Vu par l'utilisateur** : le VPN revient après qu'Android a relancé le service.
- **Cause** : quand Android relance un service "collant", il le relance sans consigne. L'ancien code traitait ce cas comme "démarrer".
- **Exigence** : une relance système sans consigne n'est acceptée que si l'utilisateur veut le VPN actif. Sinon le service s'arrête tout de suite.
- **Réf** : 5244149e, 6afae8ae, `6.0.0`.

### 2.3 Mise à jour en arrière-plan qui réactive le root
- **Vu par l'utilisateur** : en root, un fichier hosts annulé par l'utilisateur revient après une mise à jour.
- **Cause** : le même appel "appliquer" était utilisé partout.
- **Exigence** : en root aussi, une mise à jour d'arrière-plan ne réécrit le fichier hosts que s'il est déjà géré par l'app.
- **Réf** : 6afae8ae.

### 2.4 L'interface dit "en marche" alors que le VPN est mort
- **Vu par l'utilisateur** : après un arrêt forcé, l'accueil propose de "mettre en pause" un VPN déjà mort. La tuile et la notification mentent aussi.
- **Cause** : le test "VPN actif ?" regardait s'il existait n'importe quel réseau VPN, donc aussi celui d'une autre app. L'état enregistré restait "en marche" après un arrêt sans rappel.
- **Exigence** : l'état affiché (accueil, tuile, notification) reflète le service de cette app, vérifié pour de vrai. Un état enregistré périmé est corrigé dès qu'on le détecte. Après un échec de démarrage, la tuile revient à l'état réel.
- **Réf** : 95e103fc, 6afae8ae, `6.6.0-c`.

### 2.5 Arrêt forcé par un gestionnaire de batterie (ColorOS)
- **Vu par l'utilisateur** : le VPN ne revient pas tant qu'on n'a pas rouvert l'app.
- **Cause** : un arrêt forcé ne déclenche aucun rappel, et Android bloque tous les moyens de redémarrage (relance du service, tâche périodique, diffusions) jusqu'à la réouverture de l'app. La tâche périodique de vérification s'appuyait sur l'état réel (corrigé en "arrêté"), donc elle se désactivait elle-même.
- **Exigence** : à l'ouverture de l'app, si l'utilisateur veut le VPN et qu'il est mort, il est relancé tout de suite. La vérification périodique se base sur l'intention de l'utilisateur, pas sur l'état réel. L'app propose une entrée "Persistance VPN" (Always-on) en mode VPN, sur mobile comme sur TV, car c'est le seul moyen qui survit à un arrêt forcé.
- **Réf** : 95e103fc, 62f43e5c, `6.6.0-c`.

### 2.6 Révocation du VPN : autre app ou système ?
- **Vu par l'utilisateur** : soit le VPN reste "en marche" à l'écran sans tunnel, soit il essaie de reprendre la place d'un autre VPN.
- **Cause** : il n'y avait pas de gestion de la révocation (arrêt incomplet). Ensuite, toute révocation était prise pour un choix de l'utilisateur, alors que les gestionnaires de batterie révoquent aussi le VPN en mettant l'app en veille.
- **Exigence** : à une révocation, l'arrêt est complet. Si une autre app a pris le VPN (l'app n'a plus l'accord), l'intention passe à "off". Si le système a révoqué alors que l'accord existe encore, l'intention reste "on" pour que la relance automatique fonctionne.
- **Réf** : 95e103fc.

### 2.7 Démarrage en premier plan refusé
- **Vu par l'utilisateur** : l'interface reste bloquée sur "pause" après une relance par le constructeur.
- **Cause** : Android peut refuser le passage en premier plan quand le processus a été tué puis relancé par le système du constructeur.
- **Exigence** : si le service ne peut pas passer en premier plan, il s'arrête proprement et annonce "arrêté", pour que l'affichage reste vrai.
- **Réf** : ca79d7bd.

### 2.8 Création du tunnel refusée
- **Vu par l'utilisateur** : tunnel coupé mais tout affiche "en marche".
- **Cause** : la création du tunnel peut renvoyer "rien" si l'accord VPN a été retiré pendant la construction. Une erreur inattendue dans la boucle tuait le fil sans annoncer l'arrêt.
- **Exigence** : un échec de création du tunnel passe par le chemin normal de reconnexion. Toute erreur inattendue arrête le VPN de façon visible (statut "arrêté" envoyé), sans boucle de relance infinie.
- **Réf** : 0d9fc329.

---

## 3. Arrêt, pause et concurrence dans le service VPN

### 3.1 Il faut appuyer deux fois sur pause
- **Vu par l'utilisateur** : le VPN revient juste après la pause.
- **Cause** : fermer le tunnel pendant une lecture bloquante produisait une erreur que la boucle prenait pour une coupure réseau, donc elle se reconnectait.
- **Exigence** : un arrêt voulu ne doit jamais être pris pour une panne réseau. Une pause arrête en une seule action.
- **Réf** : dd097886, `6.6.0-c`.

### 3.2 Un changement de réseau juste après la pause relance le VPN
- **Vu par l'utilisateur** : VPN en pause, le Wi-Fi bouge, le VPN revient.
- **Cause** : l'arrêt du service n'est pas immédiat. Les événements réseau déjà en file passaient après et voyaient "tunnel coupé, réseau présent", donc relançaient. Le fil relancé survivait au service.
- **Exigence** : l'app distingue "coupé en attente de réseau" (on récupère) et "arrêté par l'utilisateur" (on ne touche à rien). Aucun événement réseau ne relance un VPN en pause.
- **Réf** : 971b8ccb, c0ef2cca, `6.0.0`, `6.6.0-c`.

### 3.3 Deux fils de travail VPN en même temps
- **Vu par l'utilisateur** : après un changement de réseau, erreurs en chaîne, puis le VPN ne revient plus jamais.
- **Cause** : au redémarrage, le nouveau fil démarrait avant que l'ancien soit vraiment arrêté. Un fil bloqué dans une attente système ne répond pas à une interruption. Les deux fils modifiaient la même file d'attente, qui a fini corrompue (taille négative), et cette corruption survivait à chaque reconnexion.
- **Exigence** : à tout moment, un seul fil de travail VPN est actif. L'ancien est retiré avant que le nouveau démarre, et un fil retiré s'en va sans rien annoncer. Les structures partagées résistent à un accès concurrent. Un nouveau tunnel vide la file des requêtes DNS de l'ancien réseau.
- **Réf** : 19590022, `6.6.0-c`.

### 3.4 Autres soucis de fil d'exécution
- **Vu par l'utilisateur** : blocages ou plantages rares.
- **Cause** : interruption perdue (bb102e86), boucle infinie qui ne sort que par exception (8faff965), double destruction du service (adec44bb), blocage mutuel possible (25aaa9f2, `5.0.2`), double création du pool de tâches (ca79d7bd).
- **Exigence** : arrêt et démarrage du moteur sont sûrs face aux appels multiples ou simultanés. Un arrêt est idempotent.
- **Réf** : bb102e86, 8faff965, adec44bb, 25aaa9f2, ca79d7bd.

### 3.5 Le journal DNS fait planter le tunnel
- **Vu par l'utilisateur** : l'app et le VPN tombent en ouvrant le journal des requêtes.
- **Cause** : la liste des domaines vus était écrite par le fil des paquets et lue par l'interface sans protection (exception de modification concurrente), et elle grossissait sans limite.
- **Exigence** : le journal DNS est borné en taille, sûr entre fils, et n'a aucun coût quand l'enregistrement est éteint.
- **Réf** : 0d9fc329.

---

## 4. Réseau et changements de réseau

### 4.1 Le tunnel se reconstruit à chaque micro-coupure mobile (ColorOS)
- **Vu par l'utilisateur** : icône VPN qui clignote sans arrêt.
- **Cause** : sur Oppo/ColorOS, le réseau mobile apparaît et disparaît toutes les quelques secondes même sur Wi-Fi. Chaque événement reconstruisait le tunnel.
- **Exigence** : l'apparition ou la perte d'un réseau secondaire ne reconstruit pas le tunnel. Seul un vrai changement du réseau utilisé par défaut le fait.
- **Réf** : 6afae8ae, 00930c5e.

### 4.2 DNS cassé quand le Wi-Fi revient pendant qu'on est en données mobiles
- **Vu par l'utilisateur** : "le VPN a coupé" pendant un moment.
- **Cause** : le Wi-Fi revient d'abord comme réseau secondaire, donc pas de reconstruction, mais Android en fait la route par défaut. Le tunnel continuait d'envoyer le DNS vers le serveur du réseau mobile, devenu injoignable.
- **Exigence** : l'app suit le même choix de réseau par défaut qu'Android (Wi-Fi préféré une fois validé) et reconstruit le tunnel quand ce réseau change.
- **Réf** : 00930c5e, `6.6.0-c`.

### 4.3 Changement de serveur DNS sans changement de type de réseau
- **Vu par l'utilisateur** : plus de résolution après un renouvellement DHCP, un changement de borne sur le même réseau Wi-Fi ou une connexion à un portail captif.
- **Cause** : le tunnel n'était reconstruit que lors d'un passage Wi-Fi/mobile.
- **Exigence** : un vrai changement des serveurs DNS utilisés reconstruit le tunnel, de façon prudente : ignorer les listes vides de transition, comparer l'ensemble réellement utilisé (après filtre IPv6), passer par le limiteur de reconnexion pour éviter les boucles.
- **Réf** : 63188c9c, `6.6.0-c`.

### 4.4 Réseau sans serveur DNS
- **Vu par l'utilisateur** : plus aucune résolution jusqu'à recréation du profil VPN.
- **Cause** : juste après un démarrage ou un changement de réseau, le réseau peut n'annoncer aucun serveur DNS. Le tunnel était créé sans résolveur.
- **Exigence** : le tunnel n'est jamais créé sans résolveur. Un résolveur public de secours est utilisé, et le tunnel est reconstruit dès que les vrais serveurs apparaissent (sauf si ce qui arrive est encore le secours).
- **Réf** : ca79d7bd, 36347927.

### 4.5 Résolveur de secours injoignable (IPv6 seul)
- **Vu par l'utilisateur** : rafale d'échecs DNS après passage en données mobiles.
- **Cause** : le secours était `1.1.1.1` (IPv4). Sur un réseau mobile IPv6 seul, il n'a pas de route.
- **Exigence** : le résolveur de secours suit la famille d'adresses que l'appareil peut vraiment joindre (selon les adresses des réseaux non-VPN), même si cela active l'IPv6 dans le tunnel alors que l'option IPv6 est coupée.
- **Réf** : 36347927, `6.6.0-c`.

### 4.6 Seul serveur DNS en IPv6 avec IPv6 désactivé
- **Vu par l'utilisateur** : plantage du VPN.
- **Cause** : le filtre IPv6 retirait le seul serveur disponible.
- **Exigence** : si le seul serveur est IPv6, il est gardé même avec l'option IPv6 coupée. Si le filtre vide la liste, on prend le secours.
- **Réf** : c2193229, `5.5.1`, 63188c9c.

### 4.7 Le tunnel lit son propre DNS ("faux DNS")
- **Vu par l'utilisateur** : DNS faux après redémarrage du VPN.
- **Cause** : le réseau actif peut être le VPN lui-même. On lisait alors les adresses DNS internes du tunnel.
- **Exigence** : les serveurs DNS en amont viennent toujours d'un réseau qui n'est pas un VPN.
- **Réf** : 6297344e, 76bb590c, `6.0.0`.

### 4.8 Détection des changements de connectivité en panne
- **Vu par l'utilisateur** : le VPN ne réagit pas aux changements de réseau.
- **Cause** : le gestionnaire de messages du service était mal câblé.
- **Exigence** : la réaction aux changements de réseau doit être couverte par un test qui envoie de vrais événements réseau au service.
- **Réf** : 3a997981, `5.10.0`, 971b8ccb (méthode de test).

### 4.9 Ethernet sur boîtier TV
- **Vu par l'utilisateur** : non signalé, mais listé comme point à vérifier.
- **Cause** : le suivi de réseau ne parle que de Wi-Fi et de mobile.
- **Exigence** : le choix du réseau par défaut prend en compte l'Ethernet (boîtiers TV).
- **Réf** : README ("Ethernet behavior on Android TV boxes"), 00930c5e.

### 4.10 VPN compté comme réseau facturé
- **Vu par l'utilisateur** : Android 11 considère la connexion comme "limitée".
- **Cause** : le tunnel était déclaré facturé par défaut.
- **Exigence** : le tunnel est déclaré non facturé.
- **Réf** : 6aae7f15, `5.3.0`.

---

## 5. DNS à l'intérieur du tunnel

### 5.1 Réponses DNS de plus de 1 Ko tronquées
- **Vu par l'utilisateur** : certains noms (beaucoup d'adresses, DNSSEC, longs TXT) ne se résolvent plus.
- **Cause** : la réponse était lue dans un tampon de 1024 octets. Android annonce 1232 octets (EDNS0), 4096 est courant.
- **Exigence** : toute réponse UDP qu'un résolveur accepte d'envoyer passe en entier (au moins la taille maximale annoncée par les clients).
- **Réf** : 7cbe99ed, `6.6.0-c`.

### 5.2 Réponse envoyée avec des octets en trop
- **Vu par l'utilisateur** : réponses corrompues.
- **Cause** : on renvoyait tout le tampon de 1024 octets au lieu des octets reçus.
- **Exigence** : seule la taille réellement reçue est renvoyée.
- **Réf** : ca79d7bd.

### 5.3 Fuite de descripteurs
- **Vu par l'utilisateur** : le VPN finit par lâcher sous forte charge DNS.
- **Cause** : l'enveloppe du socket de chaque requête n'était jamais fermée.
- **Exigence** : chaque requête libère toutes ses ressources système, dans tous les cas (réponse, erreur, délai dépassé).
- **Réf** : ca79d7bd.

### 5.4 Un paquet mal formé fait tomber tout le tunnel
- **Vu par l'utilisateur** : VPN coupé sans raison.
- **Cause** : n'importe quelle app peut écrire n'importe quoi dans le tunnel. Une étape de remise en forme (pcap4j 1.8.2) lançait une exception non capturée qui arrêtait le fil sans relance. Un fuzzing a trouvé 13 139 échecs sur 800 000 paquets.
- **Exigence** : tout paquet entrant est considéré comme hostile. Un paquet qu'on ne sait pas traiter est jeté, sans effet sur le reste. Le moteur DNS doit être testé par fuzzing.
- **Réf** : 81ec80d3, `6.6.0-c`.

### 5.5 File de requêtes qui dépasse sa limite
- **Vu par l'utilisateur** : rien de visible.
- **Cause** : comparaison stricte faite avant l'ajout, donc 1025 requêtes au lieu de 1024.
- **Exigence** : les limites de file sont respectées exactement.
- **Réf** : 7cbe99ed.

### 5.6 Réponse DNS acceptée depuis n'importe qui
- **Vu par l'utilisateur** : risque d'usurpation des réponses.
- **Cause** : le socket de requête n'était pas lié au résolveur, donc une réponse venant de n'importe quelle adresse était acceptée et remise à l'app.
- **Exigence** : une réponse n'est acceptée que si elle vient du résolveur interrogé.
- **Réf** : 0d9fc329.

---

## 6. Surveillance, vérification périodique et reconnexion

### 6.1 Reconnexion toutes les 5 à 20 minutes sur réseau stable
- **Vu par l'utilisateur** : avec "surveiller la connexion", le VPN se reconnecte sans raison.
- **Cause** : le seul signal de vie était "dernier paquet reçu", mis à jour seulement par les vraies requêtes. Téléphone au repos, rien ne passe, donc le tunnel était jugé mort. Ensuite, chaque requête d'arrière-plan remettait l'intervalle de sonde à 1 s, ce qui réveillait la radio sans cesse.
- **Exigence** : un tunnel sans trafic n'est pas un tunnel mort. Seul un vrai échec réseau déclenche une reconnexion. Les sondes s'espacent quand rien ne se passe et ne sont pas remises à zéro par le trafic.
- **Réf** : a8d5e8a3, `6.6.0-c`.

### 6.2 Fausse détection "interface coupée" (ColorOS, Android 16)
- **Vu par l'utilisateur** : redémarrage du VPN toutes les 10 s, icône qui clignote.
- **Cause** : le test "interface active ?" sur un objet mis en cache échoue (ENODEV) ou se périme, alors que le tunnel va bien.
- **Exigence** : la vivacité du tunnel se juge sur la présence d'une interface tunnel. Un test qui échoue compte comme "toujours actif". Avant que le tunnel existe, il n'y a rien à surveiller.
- **Réf** : 050fcd6b, 179659a6 (le nom de l'interface change en cours de route), `6.6.0-c`.

### 6.3 Limiteur de reconnexion qui pénalise l'utilisateur
- **Vu par l'utilisateur** : après plusieurs changements 5G/Wi-Fi, VPN absent jusqu'à 128 s. Ou un démarrage manuel qui attend 60 à 128 s.
- **Cause** : le délai entre tentatives s'accumulait et n'était pas remis à zéro au retour de la connectivité ni lors d'une action explicite.
- **Exigence** : le limiteur ne sert qu'à amortir les tempêtes de reconnexion automatiques. Une action de l'utilisateur ou un retour explicite de la connectivité démarre le tunnel tout de suite.
- **Réf** : 6afae8ae, 050fcd6b.

### 6.4 Moniteur mort après son propre redémarrage
- **Vu par l'utilisateur** : plus de surveillance après une première relance.
- **Cause** : la nouvelle tâche de surveillance sortait aussitôt car son drapeau "en marche" n'était pas réarmé.
- **Exigence** : la surveillance reste active après chaque relance qu'elle provoque. Une erreur de socket remet le moniteur à zéro.
- **Réf** : 050fcd6b, 6e871a90.

### 6.5 Vérification périodique qui relance un VPN en pause ou qui reste active
- **Vu par l'utilisateur** : VPN relancé pendant une pause.
- **Cause** : la vérification ne tenait pas compte de la pause, et elle était (re)lancée au lieu d'être arrêtée à l'arrêt du service.
- **Exigence** : la vérification périodique ne relance que si l'utilisateur veut le VPN actif, et elle est arrêtée quand l'utilisateur coupe.
- **Réf** : 1db9086d, 1794b951, 95e103fc.

### 6.6 Besoin d'un bouton de secours
- **Vu par l'utilisateur** : tunnel coincé (pas d'Internet, erreurs DNS).
- **Cause** : tunnel créé dans un mauvais état après une transition réseau.
- **Exigence** : un bouton "Réinitialiser le VPN" reconstruit le tunnel à la demande et dit clairement s'il n'était pas actif.
- **Réf** : db8d7553, `6.6.0-c`.

---

## 7. Synchronisation des sources et import des listes

### 7.1 Import coupé en route marqué "à jour"
- **Vu par l'utilisateur** : une source reste figée à une taille partielle (parfois vide) et dit "à jour".
- **Cause** : les échecs de lecture puis d'insertion étaient enregistrés dans les logs puis oubliés. La source était datée comme synchronisée. Une simple coupure de connexion suffisait.
- **Exigence** : une source n'est marquée "à jour" que si elle a été lue et enregistrée en entier. Tout échec partiel compte comme un échec et sera retenté.
- **Réf** : 0d9fc329, 3bf80316, `6.6.0-c`.

### 7.2 Échec partiel silencieux
- **Vu par l'utilisateur** : la TV bloque environ 28k domaines contre environ 100k sur téléphone, sans aucun message.
- **Cause** : la synchro n'est en échec que si toutes les sources échouent. Une source qui échoue seule passe inaperçue. En plus, l'accueil TV n'affichait aucune erreur.
- **Exigence** : chaque source en échec est visible pour l'utilisateur (même si d'autres ont réussi), sur mobile comme sur TV.
- **Réf** : e26304a7, 0d9a618c, `6.6.0-c`.

### 7.3 Mauvais message d'erreur, pas de bouton "Réessayer"
- **Vu par l'utilisateur** : "vérifiez votre connexion Internet" alors que l'appareil est en ligne (deux sources hors délai à 10 s).
- **Cause** : l'erreur "téléchargement échoué" était reliée par erreur au texte "pas de connexion".
- **Exigence** : chaque type d'erreur a son propre message exact. Pour les erreurs de téléchargement ou de connexion, la fenêtre d'erreur propose directement "Réessayer la synchro".
- **Réf** : 7265519e, `6.6.0-c`.

### 7.4 Source énorme ou hostile
- **Vu par l'utilisateur** : l'app est tuée faute de mémoire, ou la synchro reste bloquée pour toujours.
- **Cause** : tout le fichier était chargé en mémoire deux fois. Un étage de traitement mort laissait les autres attendre sans fin. Avant, une expression régulière pouvait provoquer un dépassement de pile sur certaines lignes.
- **Exigence** : l'import d'une source utilise une mémoire bornée, quelle que soit la taille du fichier. Chaque import a une durée maximale. Le parseur ne peut pas exploser sur une ligne piégée. Les fichiers de plus d'un million d'entrées doivent passer.
- **Réf** : 0d9fc329, 709b50bd, ff6b514a, `5.1.0`, `5.2.0`.

### 7.5 Erreur réseau ou d'entrée/sortie qui plante l'app
- **Vu par l'utilisateur** : plantage pendant la mise à jour des sources (TLS, délai dépassé).
- **Cause** : exceptions non gérées pendant la lecture.
- **Exigence** : aucune erreur réseau, TLS ou de lecture ne fait planter l'app. Elle devient une source en échec.
- **Réf** : 72bbffa1, `5.7.0`.

### 7.6 Fichier local dont l'accès a été retiré
- **Vu par l'utilisateur** : plantage avec une source "fichier local".
- **Cause** : l'autorisation sur le fichier peut être retirée ou l'app fournisseur désinstallée. L'ouverture renvoyait "rien" ou une exception non gérée qui tuait le processus.
- **Exigence** : une source locale illisible devient une source en échec, jamais un plantage.
- **Réf** : ca79d7bd, 0d9fc329, b99876f7, `5.3.0`, `4.0.11`, `4.0.12`.

### 7.7 URL Gist mal formée
- **Vu par l'utilisateur** : plantage.
- **Cause** : vérification du nombre de segments du chemin fausse (2 au lieu de 3).
- **Exigence** : une URL de source invalide est refusée avec un message, jamais un plantage. Les URL invalides ne sont ni enregistrées ni restaurées.
- **Réf** : ca79d7bd, `5.0.10`.

### 7.8 Cache HTTP qui efface la date de mise en ligne
- **Vu par l'utilisateur** : date de la source vide ou fausse.
- **Cause** : sur une réponse "304 non modifié" sans en-tête de date, la date connue était remplacée par rien.
- **Exigence** : l'app utilise les en-têtes de cache (ETag, If-Modified-Since) pour économiser les données, et une réponse "non modifié" garde la date déjà connue.
- **Réf** : 97dceb5e, 34c37b61, `6.0.3`.

### 7.9 Trop de données pour connaître la date d'une source GitHub
- **Vu par l'utilisateur** : consommation de données.
- **Cause** : l'API renvoyait toute la liste des commits (130 Ko) au lieu d'un seul (4 Ko).
- **Exigence** : la vérification de date d'une source hébergée sur Git ne demande que le dernier commit. Une API injoignable n'est pas une erreur grave.
- **Réf** : b80323a0, 9de96b1d, `6.0.3`.

### 7.10 Dates et fuseaux horaires
- **Vu par l'utilisateur** : "mis à jour il y a -5 minutes", dates décalées.
- **Cause** : fuseaux horaires mal gérés, serveurs dont l'heure est dans le futur.
- **Exigence** : les dates des sources sont gérées avec fuseau horaire, et une date serveur dans le futur ne donne jamais une durée négative.
- **Réf** : `5.0.2`, `5.0.3`, `5.0.5`, 662ae824.

### 7.11 Statuts faux
- **Vu par l'utilisateur** : "mise à jour disponible" sans fin quand une source a échoué, "téléchargement échoué" quand aucune source n'est activée, nombre d'hôtes insérés faux.
- **Cause** : cas limites non gérés.
- **Exigence** : le statut est juste dans tous les cas : aucune source activée, une source en échec, compteurs exacts.
- **Réf** : `4.0.10`, 9df07318.

### 7.12 Mise à jour automatique qui ne fait rien
- **Vu par l'utilisateur** : les sources ne se mettent pas à jour toutes seules, ou les listes ne sont pas installées par la tâche de fond.
- **Cause** : bugs de la tâche de fond (non détaillés).
- **Exigence** : la mise à jour automatique est testée de bout en bout, y compris la condition "seulement en Wi-Fi" et l'attente d'une connexion.
- **Réf** : `4.0.10`, `5.0.8`, `5.0.2`, `4.2.4`, `1.35`.

### 7.13 Délai réseau trop court ?
- **Vu par l'utilisateur** : deux sources (adaway.org, pgl.yoyo.org) hors délai alors que l'appareil est en ligne.
- **Cause** : délai par défaut de 10 s, laissé tel quel.
- **Exigence** : le délai de téléchargement doit être pensé pour des serveurs lents. L'échec doit être retentable facilement.
- **Réf** : 7265519e.

---

## 8. Règles, listes et fichier hosts généré

### 8.1 Entrées "autorisées" perdues à cause d'un commentaire
- **Vu par l'utilisateur** : des entrées d'autorisation venant d'une source disparaissent.
- **Cause** : le commentaire `#` en fin de ligne n'était retiré que s'il était en 2e position.
- **Exigence** : un commentaire en fin de ligne est retiré quelle que soit sa position, pour tous les types de lignes.
- **Réf** : ca79d7bd, `6.6.0-c`.

### 8.2 Injection de lignes dans le fichier hosts
- **Vu par l'utilisateur** : risque de sécurité (en root, le fichier devient `/system/etc/hosts`).
- **Cause** : un libellé ou une URL de source venant d'une sauvegarde pouvait contenir un retour à la ligne, recopié tel quel dans le fichier.
- **Exigence** : toute valeur écrite dans le fichier hosts (libellé, URL, domaine, redirection) est validée. Retour à la ligne interdit, domaine et IP au bon format.
- **Réf** : 0d9fc329.

### 8.3 Bugs historiques de génération
- **Vu par l'utilisateur** : doublons, hôte et IP inversés, listes blanche et noire inversées, domaine encore bloqué après désactivation de sa source, source réactivée non appliquée, listes perso perdues ou non prises en compte avant une mise à jour des sources.
- **Cause** : bugs successifs du calcul de la liste.
- **Exigence** : le calcul de la liste finale est une fonction testée : sans doublon, ordre correct, prise en compte immédiate des listes perso et de l'activation ou désactivation des sources.
- **Réf** : `4.0.6`, `5.0.2`, `5.0.3`, `5.0.4`, `5.0.6`, `5.0.8`.

### 8.4 Domaines complexes refusés
- **Vu par l'utilisateur** : impossible d'ajouter certains domaines.
- **Cause** : validation trop stricte.
- **Exigence** : la validation des domaines accepte les noms complexes et les jokers là où ils sont permis (listes d'autorisation).
- **Réf** : `4.0.9`, 0d9fc329.

### 8.5 Bouton "Appliquer" affiché à tort
- **Vu par l'utilisateur** : "appliquez votre configuration" après une synchro d'arrière-plan, alors que rien n'a été modifié.
- **Cause** : l'affichage réagissait à tout changement des données.
- **Exigence** : l'invitation à appliquer n'apparaît qu'après une vraie modification par l'utilisateur, et elle est accessible au D-pad.
- **Réf** : e4aa15b7, `5.0.5`.

### 8.6 Nouvelle source avec "autorisations" activées
- **Vu par l'utilisateur** : une nouvelle source débloque des domaines sans qu'on le veuille.
- **Cause** : valeur par défaut.
- **Exigence** : à la création d'une source, la prise en compte de ses entrées "autorisées" est désactivée par défaut.
- **Réf** : `5.0.8`.

### 8.7 Sources http
- **Vu par l'utilisateur** : risque de sécurité.
- **Cause** : sources non chiffrées acceptées.
- **Exigence** : les sources en `http://` sont refusées.
- **Réf** : `4.0.9`.

---

## 9. Mode root

### 9.1 Fichier hosts introuvable sur Android 13
- **Vu par l'utilisateur** : installation du fichier hosts impossible.
- **Cause** : le chemin système était déduit d'une propriété Java (`java.home`) qui a changé.
- **Exigence** : le chemin du fichier hosts système est fixe (`/system/etc/hosts`), pas déduit de l'environnement.
- **Réf** : c205dbb8, `5.12.1`.

### 9.2 Commandes shell
- **Vu par l'utilisateur** : échecs selon l'appareil.
- **Cause** : dépendance à des outils qui ne sont pas partout.
- **Exigence** : n'utiliser que les commandes shell de base présentes depuis Android 8.
- **Réf** : c6d68716.

### 9.3 Message root peu clair
- **Vu par l'utilisateur** : on ne sait pas quel module Magisk installer.
- **Cause** : message d'erreur vague.
- **Exigence** : l'erreur dit exactement quel module systemless est nécessaire quand la partition système est en lecture seule.
- **Réf** : 4f1f1391, `4.3.1`.

### 9.4 Installation sur stockage externe
- **Vu par l'utilisateur** : tcpdump ou le serveur web ne se lancent pas.
- **Cause** : sur stockage externe, les binaires ne peuvent pas être exécutés.
- **Exigence** : l'app ne peut pas être installée sur stockage externe. Les binaires lancés par l'app doivent être extraits sur l'appareil.
- **Réf** : 94dc9edb, `4.2.3`, 3fed386d.

### 9.5 État "appliqué" persistant après réinstallation
- Voir 1.3. **Exigence** supplémentaire : l'état root se lit à partir d'une source sûre, et une ancienne installation ne doit pas tromper la nouvelle.
- **Réf** : e26304a7.

---

## 10. Serveur web local et certificat (root)

### 10.1 Serveur qui ne démarre pas à la première activation
- **Vu par l'utilisateur** : interrupteur activé, mais le serveur ne répond pas tant qu'on ne le désactive et réactive pas.
- **Cause** : le serveur tourne en processus root détaché, qui survit à une mise à jour ou réinstallation de l'app et garde les ports 80 et 443. Le nouveau ne peut pas écouter et s'arrête. Le test "en marche ?" cherche juste un nom de processus et trouve l'ancien.
- **Exigence** : au démarrage, toute ancienne instance est arrêtée d'abord. Le test "en marche" vérifie que c'est bien l'instance lancée et qu'elle répond (pas seulement qu'un processus du même nom existe). Les échecs du test HTTPS sont journalisés.
- **Réf** : 7afdc456, `6.6.1-c`, `5.0.3` ("web server not start on install").

### 10.2 Interrupteur qui revient en arrière
- **Vu par l'utilisateur** : l'interrupteur du serveur web ne reste pas coché.
- **Cause** : on vérifiait la liste des processus juste après avoir lancé la commande en arrière-plan, avant que le processus existe, donc le changement était refusé.
- **Exigence** : l'interrupteur accepte le choix tout de suite, puis l'état réel est vérifié en arrière-plan après un délai, et corrigé seulement s'il ne correspond pas.
- **Réf** : 15e6d07a, `6.6.1-c`.

### 10.3 Ressources périmées après une mise à jour
- **Vu par l'utilisateur** : ancien certificat (et autres fichiers) servis pour toujours.
- **Cause** : les ressources du serveur (certificat, clé, icône, page de test) n'étaient copiées depuis l'APK que la première fois. Avant ce correctif, l'astuce était de changer le nom du fichier certificat à chaque renouvellement.
- **Exigence** : les ressources fixes de l'app sont recopiées à chaque version, en écrasant les anciennes.
- **Réf** : 15e6d07a, 2803b96a.

### 10.4 Installation du certificat refusée ("clé privée requise")
- **Vu par l'utilisateur** : Android demande une clé privée pour installer le certificat.
- **Cause** : le certificat régénéré avait perdu l'indication "autorité de certification" (CA), alors que la configuration la demandait. Android le traitait comme un certificat personnel.
- **Exigence** : le certificat livré est toujours une autorité de certification valide. Sa génération est vérifiée automatiquement (présence du drapeau CA) avant publication.
- **Réf** : 217c3d1d, `6.6.1-c`.

### 10.5 Durée de validité du certificat
- **Vu par l'utilisateur** : certificat refusé ou expiré.
- **Cause** : validité de 10 ans refusée par les règles des navigateurs, réduite à 37 mois. Il a ensuite fallu le renouveler.
- **Exigence** : la durée de validité respecte les limites des navigateurs, et l'expiration est suivie pour renouveler à temps.
- **Réf** : 34e3bcd7, 2803b96a, 806c2f6a, `5.9.0`, `6.1.4`.

### 10.6 Installation du certificat sur Android 11 et plus
- **Vu par l'utilisateur** : installation directe impossible.
- **Cause** : depuis Android 11, une app ne peut plus installer un certificat directement. Il faut l'exporter dans un fichier que l'utilisateur installe depuis les réglages.
- **Exigence** : sur Android 11 et plus, le certificat est exporté dans un fichier choisi par l'utilisateur, avec les explications pour l'installer.
- **Réf** : ccc18a60, `5.0.10`.

### 10.7 Serveur démarré sans demande
- **Vu par l'utilisateur** : interrupteur coché à l'installation alors que rien ne tourne.
- **Cause** : la valeur par défaut était "activé", mais rien ne lançait le serveur à l'installation.
- **Exigence** : le serveur web est désactivé par défaut. L'interrupteur affiché reflète toujours l'état réel.
- **Réf** : a4edb2f6, `6.6.1-c`.

### 10.8 Robustesse du serveur
- **Vu par l'utilisateur** : serveur tué en manque de mémoire, TLS cassé après une mise à jour de la bibliothèque, arrêt mal géré.
- **Cause** : réglage du "tueur mémoire" (plage changée de -17 à -1000/1000), migration TLS de Mongoose, gestion des signaux, chemins écrits dans des tampons fixes de 100 octets dans un processus root (débordement possible).
- **Exigence** : le serveur ne doit pas être tué en premier en manque de mémoire, gère proprement l'arrêt, sert le HTTPS de façon testée (script de test), n'écoute que sur l'adresse locale, et n'a aucun débordement de tampon possible.
- **Réf** : 8f5ae846, 62959112, a0717cfa, 7c3ec3b1, f9a54c12, 0d9fc329, `1.37`, `2.1`.

---

## 11. Mises à jour de l'application

### 11.1 Installation qui échoue la première fois ("sources inconnues")
- **Vu par l'utilisateur** : le téléchargement réussit, l'utilisateur donne la permission, puis rien ne s'installe.
- **Cause** : après l'accord, Android ne relance pas l'installation.
- **Exigence** : avant d'installer, l'app vérifie la permission. Si elle manque, elle envoie l'utilisateur aux réglages puis reprend l'installation au retour. Si l'utilisateur revient sans accorder, l'écran redevient utilisable.
- **Réf** : bbb1b714, `6.5.1-c`.

### 11.2 Processus tué pendant que l'utilisateur est dans les réglages
- **Vu par l'utilisateur** : sur Shield TV, retour des réglages, l'APK téléchargé a disparu, il faut recommencer.
- **Cause** : le nettoyage au démarrage effaçait l'APK. Le retour de l'écran des réglages était perdu car l'écran d'origine avait été détruit. Android ne restaurait que l'écran d'accueil.
- **Exigence** : le flux d'installation survit à la mort du processus. L'état "installation en attente" est enregistré, et n'importe quel écran restauré reprend l'installation.
- **Réf** : 5d2d3bbf, 87cc096b.

### 11.3 APK non vérifié
- **Vu par l'utilisateur** : risque de sécurité.
- **Cause** : l'APK était stocké dans un dossier partagé où, sur Android 8 à 10, une autre app peut écrire. Un fichier partiel (téléchargement interrompu) était aussi passé à l'installateur. Un APK déjà présent était réutilisé.
- **Exigence** : avant installation, l'APK doit être complet, déclarer le bon paquet et être signé avec la même clé que l'app installée. Il est copié en zone privée avant vérification et installation. Un fichier trouvé sur disque n'est jamais réutilisé sans nouvelle vérification. Les copies sont effacées à la fin.
- **Réf** : 0d9fc329.

### 11.4 Lancement de l'installateur bloqué en arrière-plan
- **Vu par l'utilisateur** : téléchargement fini, rien ne se passe.
- **Cause** : depuis Android 10, une app en arrière-plan ne peut pas ouvrir un écran.
- **Exigence** : l'installateur est lancé depuis un écran visible.
- **Réf** : 00189ebc.

### 11.5 Plantages du téléchargement
- **Vu par l'utilisateur** : plantage en touchant "Mettre à jour".
- **Cause** : permission `DOWNLOAD_WITHOUT_NOTIFICATION` manquante (obligatoire depuis Android 12 pour un téléchargement caché), division par zéro quand la taille est inconnue, plantage si le téléchargement est instantané, téléchargement interrompu mal géré.
- **Exigence** : la progression gère une taille inconnue, un téléchargement instantané et un téléchargement interrompu. Toute permission liée à un mode de téléchargement est déclarée.
- **Réf** : a35398dd, fc8de373, dc5988a2, 989911e7, `6.0.0`, `6.1.2`.

### 11.6 Bouton "Ouvrir" de l'installateur cassé sur TV
- **Vu par l'utilisateur** : sur Android TV et Shield, "Ouvrir" après la mise à jour ne fait rien.
- **Cause** : l'installateur garde l'ancien chemin de l'APK. De plus, sur TV, la notification peut être invisible (permission non donnée ou volet absent).
- **Exigence** : après une mise à jour, l'utilisateur est prévenu même sans notification (message à l'écran), avec un raccourci qui relance l'app correctement (lanceur TV ou mobile). Ce message est omis si l'app s'ouvre d'elle-même dans les 5 s.
- **Réf** : 5bdd0c27, 62dd3fda, 29589f4d, `6.5.1-c`.

### 11.7 Comparaison de versions et notes de version
- **Vu par l'utilisateur** : notes affichées avec des `**` bruts.
- **Cause** : le texte des releases GitHub est en Markdown.
- **Exigence** : les versions se comparent segment par segment (6.5.10 est plus récent que 6.5.9). Les notes de version sont affichées en Markdown.
- **Réf** : 4b588247, f41e6b76.

### 11.8 Notifications qui s'écrasent
- **Vu par l'utilisateur** : la notification "nouvelle version" remplace celle des sources.
- **Cause** : même identifiant de notification, même canal.
- **Exigence** : chaque type de notification a son identifiant et son canal (sources, nouvelle version, VPN), pour pouvoir couper l'un sans l'autre.
- **Réf** : 8d64bafb, `6.5.1-c`.

### 11.9 Vérification au démarrage absente sur TV
- **Vu par l'utilisateur** : la bannière de mise à jour n'apparaît jamais sur TV.
- **Cause** : l'écran TV ne lançait pas la vérification.
- **Exigence** : toute fonction de l'accueil mobile existe aussi sur l'accueil TV (vérification de mise à jour, erreurs, réglages).
- **Réf** : e056c514, 74e54e8d, 0d9a618c.

### 11.10 Rotation pendant la mise à jour
- **Vu par l'utilisateur** : écran de mise à jour cassé après rotation.
- **Exigence** : l'écran de mise à jour garde son état à la rotation.
- **Réf** : d9e1b63e, `5.5.1`.

---

## 12. Sauvegarde, restauration et vie privée

### 12.1 La sauvegarde Android tuait l'app
- **Vu par l'utilisateur** : VPN arrêté pendant les sauvegardes, et sauvegardes jamais envoyées.
- **Cause** : la sauvegarde automatique tue l'app pendant l'opération et dépassait le quota de 25 Mo (base entière).
- **Exigence** : rien ne quitte l'appareil tout seul : pas de sauvegarde Google Drive ni de transfert d'appareil à appareil (Android 12 et plus). L'export et la restauration se font à la demande, vers un fichier choisi.
- **Réf** : 31d0b78b, 0d9fc329, `5.12.0`.

### 12.2 Restauration qui échoue sur une entrée
- **Vu par l'utilisateur** : toute la restauration est perdue pour une seule valeur invalide, ou des doublons apparaissent.
- **Exigence** : une entrée invalide est ignorée sans annuler le reste. Une restauration n'ajoute pas de doublons.
- **Réf** : 0d9fc329, d3fb4e97, `5.0.3`.

### 12.3 Fichier de sauvegarde non proposé à la restauration
- **Vu par l'utilisateur** : sur les anciens appareils, le fichier n'apparaît pas dans le sélecteur.
- **Cause** : type de fichier JSON mal reconnu avant Android 10.
- **Exigence** : le sélecteur de restauration montre le fichier de sauvegarde sur toutes les versions supportées.
- **Réf** : 52a649d6, `5.7.0`.

### 12.4 Télémétrie et journal de diagnostic
- **Vu par l'utilisateur** : envoi de rapports sans contrôle possible, journal de diagnostic actif à son insu.
- **Cause** : l'interrupteur de télémétrie était caché en version publique, alors qu'une sauvegarde restaurée pouvait l'activer. Le journal enregistrait tout, et des lignes d'erreur contenaient encore des noms de domaines.
- **Exigence** : pas de télémétrie. Le journal de diagnostic est éteint par défaut, activé seulement par l'utilisateur, limité en taille, et ne contient jamais de nom de domaine visité. Le journal ne doit pas être noyé par une ligne par requête DNS.
- **Réf** : 06b4ab75, bc32da44, 8528730d, 0d9fc329.

### 12.5 Logs de debug en version publique
- **Exigence** : les traces de debug ne partent jamais dans une version publique ni dans le journal exportable. La catégorie "Debug" des préférences est cachée hors version debug.
- **Réf** : 43724cb2, 4e026d56.

---

## 13. Android TV

### 13.1 Écrans du téléphone inutilisables à la télécommande
- **Vu par l'utilisateur** : bouton flottant inatteignable, case à cocher et clic qui font deux choses, appui long nécessaire, menu de barre d'action.
- **Exigence** : chaque écran TV est conçu pour le D-pad : boutons dans l'en-tête, un clic ouvre une fenêtre d'actions, chemin de focus voulu à travers chaque contrôle.
- **Réf** : c128ff9e, 9a4f4012, b2e99786, `6.6.0-c`.

### 13.2 Barre de message ("snackbar") inatteignable
- **Cause** : le focus n'entre jamais dans une snackbar.
- **Exigence** : aucune action importante n'est placée dans une snackbar sur TV.
- **Réf** : e4aa15b7.

### 13.3 Champ texte qui mange les appuis
- **Vu par l'utilisateur** : sur le champ adresse, il faut appuyer plusieurs fois pour sortir, et le curseur reste au milieu.
- **Cause** : un champ d'une ligne garde haut et bas pour son curseur.
- **Exigence** : haut et bas quittent le champ directement, et le curseur se place en fin de texte à l'arrivée.
- **Réf** : b2e99786.

### 13.4 Focus qui s'arrête sur les lignes désactivées des réglages
- **Cause** : une préférence désactivée reste sélectionnable par le focus.
- **Exigence** : une ligne désactivée n'est pas sélectionnable au D-pad.
- **Réf** : 8dd343b7.

### 13.5 Focus perdu
- **Cause** : changer la visibilité d'une page ne déplace pas le focus. Un lien de focus vers une vue cachée bloque le focus. Les onglets de l'aide n'étaient pas navigables.
- **Exigence** : chaque transition place le focus explicitement. Aucun lien de focus ne vise un élément caché. Les onglets sont le point d'entrée du focus (gauche/droite change d'onglet, bas entre dans le contenu, haut depuis le haut du contenu revient aux onglets).
- **Réf** : a2f6c5d4, 62f43e5c, 1e024224.

### 13.6 Focus invisible ou mal dessiné
- **Cause** : surbrillance de la même couleur que le fond, ombre ou animation qui dessinent un rectangle au lieu de la forme arrondie, icône teintée invisible sur fond blanc.
- **Exigence** : le focus est toujours bien visible, dans une couleur qui contraste avec le fond, et suit la forme du bouton.
- **Réf** : 21d6e0d2, b70a73b5.

### 13.7 Écrans qui débordent
- **Vu par l'utilisateur** : boutons coupés en bas, libellés français tronqués, barre de progression hors écran sur TV 4K.
- **Cause** : sur une TV à haute densité, l'écran ne fait que 540 à 720 dp de haut. Les textes français sont longs.
- **Exigence** : tout l'accueil TV tient sur un écran 1080p et reste défilable au D-pad si besoin. La progression est toujours visible (près de l'en-tête). Les mises en page sont testées avec les textes les plus longs (français).
- **Réf** : 61423bd6, ad63beca, 061180c3, 329beef9, 7129abce.

### 13.8 Indicateur d'onglet mal placé
- **Cause** : le calcul supposait des onglets de largeur égale, faux sur un écran large.
- **Exigence** : l'indicateur suit la position réelle de l'onglet.
- **Réf** : a93d67ed.

### 13.9 Réglages VPN cachés sur TV
- **Vu par l'utilisateur** : impossible d'activer "Always-on".
- **Cause** : la Shield et plusieurs Google TV cachent l'écran système des VPN.
- **Exigence** : l'app vérifie si l'écran existe. Sinon, elle affiche les commandes ADB exactes (avec le vrai nom de paquet) et garde la fenêtre ouverte. Elle affiche si Always-on est déjà configuré pour elle. Une lecture interdite de ce réglage donne "non configuré", jamais un plantage.
- **Réf** : 99ad2943, 760f7d62, 0eb659b2.

### 13.10 Commandes ADB fausses ou nuisibles
- **Cause** : `settings put ... ""` est rejeté (les guillemets vides sont retirés par le shell). Le conseil "lockdown" bloque tout le trafic quand le tunnel est absent (coupure de 1 à 3 s à chaque démarrage), ce qui est inadapté à un bloqueur de pubs.
- **Exigence** : les commandes proposées sont testées sur un vrai shell (`settings delete` pour désactiver), et l'app ne propose pas le mode "lockdown".
- **Réf** : b3635a41, 061180c3.

### 13.11 Fenêtre de persistance trop insistante
- **Cause** : elle s'ouvrait seule à la première activation, avec un texte qui laissait croire à une configuration obligatoire.
- **Exigence** : l'aide à la persistance est présentée comme du dépannage, accessible en un clic, jamais ouverte d'office.
- **Réf** : 6330f78e, 6c9617fc, fb3d61a0.

### 13.12 Libellés propres au root en mode VPN
- **Cause** : "Mettre à jour les hosts" laisse croire que l'app modifie le fichier système, faux en VPN (cas courant sur TV).
- **Exigence** : les libellés sont neutres ("listes de blocage") ou adaptés au mode actif.
- **Réf** : 65762975.

### 13.13 Thème et branding
- **Cause** : la TV n'a souvent pas de réglage clair/sombre, donc "suivre le système" donnait le thème clair. La bannière du lanceur avait un rouge différent.
- **Exigence** : thème sombre par défaut sur TV (avec bouton pour changer), couleurs de marque identiques partout.
- **Réf** : cbe7c92b, b144e7ca.

### 13.14 Redirection vers l'interface TV
- **Exigence** : sur TV, quelle que soit la façon d'ouvrir l'app (raccourci, intention externe), l'utilisateur arrive sur l'interface TV.
- **Réf** : b144659f.

### 13.15 Accueil TV qui plante au lancement
- **Vu par l'utilisateur** : plantage de l'accueil TV redessiné.
- **Cause** : non documentée.
- **Exigence** : l'accueil TV est testé au lancement sur un vrai appareil TV avant chaque publication.
- **Réf** : `6.6.0-c`.

---

## 14. Interface, thème, rotation

### 14.1 Plantage en changeant clair/sombre
- **Vu par l'utilisateur** : plantage sur les écrans Bloqués/Autorisés/Redirigés.
- **Cause** : à la recréation, un flux de pagination déjà lu était relu ("collect twice").
- **Exigence** : chaque écran supporte la recréation (thème, rotation, langue) sans plantage ni perte d'état.
- **Réf** : 63013c2f.

### 14.2 Couleurs de barres système fausses en mode sombre
- **Cause** : en Android, un style redéfini pour le mode nuit remplace tout le style au lieu de le compléter. Une couleur déclarée seulement en mode clair disparaît en mode sombre.
- **Exigence** : barre d'état et barre de navigation ont la bonne couleur sur tous les écrans, dans les deux modes, y compris sur Android "pur" (Pixel) qui met du noir par défaut.
- **Réf** : 63013c2f, 1dba9b7f, 26583a51.

### 14.3 Titre illisible sur l'en-tête rouge
- **Cause** : la couleur du titre de la barre d'action ne vient pas de l'attribut qu'on croyait.
- **Exigence** : tout texte et toute icône sur fond rouge sont blancs, dans les deux modes.
- **Réf** : 1886dc6f.

### 14.4 Texte réduit jusqu'à être illisible
- **Cause** : une seule ligne avec réduction automatique jusqu'à 8sp.
- **Exigence** : un texte trop long passe à la ligne avant d'être réduit, et ne descend pas sous une taille lisible.
- **Réf** : 803e7a52.

### 14.5 Textes coupés sur l'accueil
- **Cause** : largeurs mal contraintes, justification, cartes à hauteur fixe en portrait seulement (coupées sur tablette et en paysage).
- **Exigence** : aucun texte d'accueil n'est coupé, sur téléphone, tablette, paysage. Le contenu défile s'il ne tient pas.
- **Réf** : 00189ebc, f494b4e7, a35398dd, 164fc033.

### 14.6 État perdu à la rotation
- **Cause** : texte d'état qui disparaît, écran de préférences dupliqué à la rotation.
- **Exigence** : voir 14.1.
- **Réf** : c8319dcb (`6.0.1`), 7ac4c0ee (`5.0.1`).

### 14.7 Préférence retirée mais toujours appelée
- **Vu par l'utilisateur** : plantage en ouvrant "Mises à jour automatiques".
- **Cause** : une entrée a été retirée de l'écran alors que le code la cherchait encore.
- **Exigence** : chaque élément d'écran attendu par le code existe. Ce lien est vérifié à la compilation ou par un test.
- **Réf** : 5d4fcb2c, `6.5.0-c`.

### 14.8 Fuite dans la tuile des réglages rapides
- **Cause** : un observateur était ajouté à chaque ouverture et jamais retiré (référence différente à chaque fois). Deux appuis rapides pouvaient lancer deux bascules.
- **Exigence** : la tuile n'accumule rien à chaque ouverture, ignore un appui pendant une bascule en cours, et gère le cas où le système l'a déjà détachée.
- **Réf** : ca79d7bd, 57e26aed, 6afae8ae.

---

## 15. Traductions

### 15.1 Erreurs de compilation des traductions
- **Cause** : apostrophe ASCII non échappée en français, catégorie de pluriel `many` qui n'existe pas en français.
- **Exigence** : les fichiers de traduction sont validés automatiquement (échappement, catégories de pluriel par langue).
- **Réf** : 2436f5d4, 8a7c3116.

### 15.2 Textes jamais envoyés aux traducteurs
- **Cause** : le fichier des textes TV n'était pas déclaré à la plateforme de traduction. Un fichier supprimé y était encore déclaré.
- **Exigence** : chaque fichier de textes est déclaré à la plateforme de traduction, et aucun fichier supprimé n'y reste.
- **Réf** : 54910644.

### 15.3 Langue par app
- **Exigence** : l'app apparaît dans "Langues des applications" d'Android 13 et plus, avec la liste de toutes les langues livrées, sans forcer de langue elle-même.
- **Réf** : 2a045820.

### 15.4 Limite de longueur des descriptions de store
- **Cause** : F-Droid n'affiche que 80 caractères pour la description courte.
- **Exigence** : la description courte reste sous 80 caractères dans toutes les langues.
- **Réf** : 86a6a36c, 3b5c38ec.

### 15.5 Textes codés en dur
- **Cause** : l'interface TV importée avait des textes en dur.
- **Exigence** : aucun texte visible n'est écrit en dur dans le code ou les mises en page.
- **Réf** : 4d9da8c7.

---

## 16. Compilation, binaires natifs et publication

### 16.1 Pages mémoire de 16 Ko
- **Cause** : Android 15 et plus, sur appareils à pages de 16 Ko, exige des binaires alignés. `max-page-size` seul ne suffisait pas, il faut aussi `common-page-size`.
- **Exigence** : tous les binaires natifs (moteur Rust compris) sont alignés sur 16 Ko. Les bibliothèques précompilées tierces doivent l'être aussi.
- **Réf** : 3fed386d, 86c38f52.

### 16.2 Binaires exécutés extraits de l'APK
- **Cause** : tcpdump et le serveur web sont lancés comme programmes, donc doivent être extraits sur l'appareil.
- **Exigence** : tout binaire lancé comme programme est extrait à l'installation.
- **Réf** : 3fed386d, 799431d1.

### 16.3 Classe de configuration non générée
- **Cause** : depuis AGP 8, `BuildConfig` n'est plus généré par défaut. Le code qui l'utilisait ne compilait plus.
- **Exigence** : la version affichée est lue de façon fiable, et la génération des infos de compilation est activée explicitement si on s'en sert.
- **Réf** : 35446779, 46e76430, fba03fa5.

### 16.4 Version publique différente de la version debug
- **Cause** : RELEASING.md insiste : "la version release ne se comporte pas comme la debug", "un vrai appareil n'est pas un émulateur".
- **Exigence** : chaque publication est testée en version release sur un vrai appareil. Les changements VPN sont testés au moins une semaine en usage réel.
- **Réf** : RELEASING.md (section 5), CONTRIBUTING.md (section IA).

### 16.5 Nom et version du fichier publié
- **Exigence** : le nom de l'APK est produit automatiquement selon la convention des releases. Le code de version reste un entier croissant, distinct du nom (suffixe `-c`).
- **Réf** : d2fd6034, a4d35ff8.

### 16.6 Exclusion d'apps du VPN sur Android 11
- **Cause** : depuis Android 11, la liste des apps n'est visible qu'avec `QUERY_ALL_PACKAGES`.
- **Exigence** : la liste des apps à exclure du VPN est complète sur Android 11 et plus.
- **Réf** : 792631b1, `5.1.0`.

---

## 17. Démarrage de l'appareil et commandes externes

### 17.1 Démarrage au boot qui plante
- **Cause** : lancer un écran depuis un récepteur de diffusion demande un drapeau "nouvelle tâche" (exception sinon depuis Android 9).
- **Exigence** : le démarrage au boot fonctionne sans plantage, et "activer au démarrage" compte comme une intention de l'utilisateur.
- **Réf** : ca79d7bd, 6afae8ae.

### 17.2 Commandes d'apps tierces
- **Exigence** : les commandes externes (automatisation) sont protégées par une permission propre, et un "démarrer" ou "arrêter" reçu ainsi compte comme une action explicite de l'utilisateur.
- **Réf** : 674cb5f6, be6a9987, 6afae8ae, `5.8.0`.

---

## Idées ou demandes évoquées mais pas faites

- **DNS chiffré (DoH)** : une preuve de concept existe (c6f362ab, `6.0.0` "initial DOH support"), puis retour au résolveur par défaut (f012e9e8). Le code DoH n'est relié à rien aujourd'hui (81ec80d3).
- **Échec partiel de synchro visible** : aujourd'hui seulement journalisé, pas montré à l'utilisateur (e26304a7).
- **Pourquoi une source échoue sur un seul appareil** : le message d'erreur TV montre l'échec mais "n'explique pas pourquoi une source échoue toujours sur cet appareil et pas les autres", à étudier avec le journal de l'appareil (0d9a618c).
- **Délais et nouvelles tentatives de téléchargement** : volontairement non modifiés (délai 10 s) alors que deux sources lentes échouaient (7265519e).
- **Bibliothèques OpenSSL précompilées** : à mettre à jour séparément si l'alerte 16 Ko persiste (3fed386d).
- **Tuile en "mode actif"** : "je regarderai si je peux utiliser le mode actif" (88924156).
- **Retours souhaités (README)** : navigation avec d'autres télécommandes, accord VPN sur d'autres versions d'Android TV, journal DNS sur TV, écran des sources sur TV, Ethernet sur boîtiers TV.
- **Contenu d'aide traduit** : FAQ et Problèmes réécrits seulement en anglais et en français, les autres langues "pourront être rafraîchies avec le temps" (8a1165bc). La section "Pourquoi ce fork" retombe sur l'anglais pour 31 langues (f93054c2).
- **Notes TODO restantes dans le code de référence** : un message d'état du VPN que personne n'écoute ("TODO BUG - Nobody is listening to this intent", `VpnService.java`), et dans `VpnWorker.java` : "Rework status notification", "Improve exception handling in work()", "Check why data could be null".
- **Version 6.1.5 amont jamais publiée** : détection Always-on, serveur web amélioré (changelog `6.1.5 - Unreleased`).

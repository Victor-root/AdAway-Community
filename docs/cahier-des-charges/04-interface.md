# 04. Interface utilisateur (mobile et Android TV)

Ce document décrit ce que voit et fait l'utilisateur. Les textes entre guillemets sont les libellés anglais de référence (langue source). Chaque texte doit passer par le système de traduction : anglais d'abord, français ensuite (une traduction française existe déjà pour presque tous les libellés et peut servir de base).

## 0. Principes généraux

### 0.1 Deux interfaces distinctes

- L'app a une interface mobile (téléphone, tablette) et une interface TV dédiée pour l'accueil, l'assistant de premier lancement, les sources, l'édition de source, les listes et le journal DNS.
- Les écrans Préférences, Aide/À propos, Mise à jour et Applications exclues du VPN sont communs aux deux. Sur TV ils restent utilisables à la télécommande (voir section 17).
- Détection TV : l'app se considère sur TV quand le système déclare le mode d'interface "télévision". Ce test est fait à l'ouverture de l'accueil, de l'aide et de l'à-propos.
- L'écran tactile et la téléphonie ne sont pas obligatoires : l'app s'installe sur une TV sans écran tactile.

### 0.2 Esprit visuel

- Couleur de marque : rouge (un rouge plus sombre en thème sombre). Accents "pastel" par catégorie, toujours les mêmes partout :
  - Bloqués : pastille rouge pâle, icône "interdit".
  - Autorisés : pastille verte pâle, icône "coche".
  - Redirigés : pastille bleue pâle, icône "double flèche".
  - Sources : pastille rouge, icône "marque-pages".
  - Journal DNS : pastille bleue, icône "règle/liste".
  - Aide : pastille ambre, icône "?".
  - Préférences : pastille grise, icône "engrenage".
  - Persistance VPN : pastille verte (mobile) ou bleue (TV), icône "clé".
  - Thème (TV) : pastille ambre, icône "luminosité".
- Cartes arrondies, icônes rondes colorées, textes secondaires en gris.
- L'assistant de premier lancement et l'écran de mise à jour ont toujours un fond rouge, quel que soit le thème.
- Animations sobres : apparitions et disparitions en fondu, déplacement animé de l'indicateur d'onglet.

### 0.3 Navigation

- Tous les écrans secondaires mobiles ont une barre de titre avec une flèche retour qui ramène à l'écran parent (accueil, ou liste des sources pour l'édition d'une source).
- Les écrans TV n'ont pas de barre de titre : un bandeau d'en-tête avec icône + titre + boutons d'action, puis le contenu. Le bouton Retour de la télécommande ferme l'écran.

## 1. Points d'entrée hors écrans

- Icône du lanceur mobile : ouvre l'accueil mobile. Si l'appareil est une TV, l'accueil mobile redirige immédiatement vers l'accueil TV.
- Lanceur TV (Leanback) : ouvre l'accueil TV, avec une bannière TV 16:9 (fond rouge de marque, logo blanc centré).
- Raccourcis d'application (appui long sur l'icône) : "Preferences", "DNS requests" (journal DNS mobile), "Your lists" (listes mobiles, onglet Bloqués).
- Tuile des réglages rapides "AdAway" : affiche actif/inactif selon que le blocage est appliqué ; un appui bascule le blocage (désactive si actif, active sinon). Un deuxième appui pendant une bascule en cours est ignoré. En cas d'échec, la tuile revient à l'état réel, sans message.
- Notifications (détaillées dans une autre zone) : la notification "Source update available" ouvre l'accueil ; "App update available" ouvre l'écran de mise à jour ; la notification VPN ouvre l'accueil et propose "Pause"/"Resume". L'ouverture de l'accueil efface les notifications de mise à jour.
- À chaque ouverture de l'accueil (mobile et TV), l'app relance le VPN s'il a été tué de l'extérieur, et reprend une installation de mise à jour en attente (voir 12).

## 2. Accueil mobile

### 2.1 Rôle

Tableau de bord : état de la protection, compteurs, sources, accès aux autres écrans, bouton principal marche/pause.

### 2.2 Contrôles à l'ouverture

- Si aucune méthode de blocage n'est choisie : ouverture de l'assistant de premier lancement, et l'accueil se ferme. Ce contrôle est refait à chaque retour sur l'écran.
- Si la méthode est VPN et que l'autorisation VPN système n'est pas (ou plus) accordée : la demande d'autorisation VPN du système s'affiche. Le résultat est ignoré.
- Au premier affichage seulement (pas après une rotation) :
  - si "Check for update at startup" (mises à jour de l'app) est actif : vérification silencieuse d'une nouvelle version ;
  - si "Check for update at startup" (mises à jour des hosts) est actif : vérification des sources (sans téléchargement), avec la progression visible dans la carte Sources.

### 2.3 Contenu, de haut en bas

1. En-tête coloré (rouge si le blocage est appliqué, gris sinon ; la barre d'état du système prend la même couleur) :
   - logo de l'app (arrondi) ;
   - titre "AdAway Community" et sous-titre "Open Source ad blocker", dont la taille de texte s'adapte à la place disponible ;
   - en haut à droite, le numéro de version de l'app. Un appui ouvre l'écran de mise à jour.
2. Trois cartes compteurs côte à côte, à cheval sur le bas de l'en-tête, chacune avec sa pastille ronde posée sur le bord supérieur :
   - "Blocked" / "Ads & trackers" : nombre de noms d'hôte distincts bloqués et actifs (toutes sources confondues, y compris la liste perso) ;
   - "Allowed" / "Exceptions" : nombre de noms distincts autorisés actifs ;
   - "Redirected" / "Custom rules" : nombre de noms distincts redirigés actifs.
   - Le nombre est affiché brut (pas de séparateur de milliers). Il se met à jour en direct.
   - Un appui ouvre l'écran des listes sur l'onglet correspondant.
3. Zone défilante (elle passe sous la barre du bas) :
   - Bannière de mise à jour (cachée par défaut) : visible seulement si une nouvelle version a été détectée. Titre "Update available", sous-titre "Tap to install X" (X = version). Un appui ouvre l'écran de mise à jour.
   - Carte Sources :
     - ligne 1 en gras : "N up-to-date source(s)" (pluriel géré) ;
     - ligne 2 : "N outdated source(s)" ;
     - à droite, bouton rond "synchroniser" (libellé accessible "Update hosts") : lance une synchronisation (télécharge ce qui a changé puis, seulement si le blocage est actif, réapplique ; ne réactive jamais un blocage coupé par l'utilisateur) ;
     - pendant une opération : une barre de progression indéterminée et une ligne de texte d'état (exemples : "Checking for update", "Checking X source for update", "Retrieving sources", "Downloading source X", "Parsing X source", "Syncing rule database", "Hosts file successfully updated", "VPN configuration successfully updated", "Source update available", "No source update found") ;
     - à la fin : la barre disparaît, le dernier texte d'état reste affiché ; en cas d'erreur le texte disparaît et un dialogue d'erreur s'ouvre ;
     - un appui sur la carte (hors bouton) ouvre l'écran des sources.
   - Rangée de trois tuiles compactes :
     - "DNS log" / "See what's blocked in real time" : ouvre le journal DNS ;
     - "Help" / "Guides and troubleshooting" : ouvre l'aide ;
     - "Preferences" / "Configure AdAway" : ouvre les préférences.
   - Carte "VPN persistence" / "Keep ad blocking running in the background" : visible uniquement en méthode VPN (vérifié à chaque retour sur l'écran). Un appui ouvre le dialogue de persistance VPN (version mobile, sans commandes ADB, voir 15.3).
4. Barre du bas : une seule forme continue (bord plat avec une large bosse douce au centre, légère ombre, fin liseré en haut) qui berce le bouton flottant principal.
   - Bouton flottant central (libellé accessible "Pause/resume ad blocking") : icône "pause" quand le blocage est appliqué, logo de l'app quand il est coupé.
   - Un appui bascule le blocage : coupe s'il est appliqué, applique sinon. Ignoré si une opération est déjà en cours.
   - Il n'y a pas d'autre bouton dans la barre.

### 2.4 Erreurs

Dialogue d'erreur (icône d'alerte) avec titre = message court de l'erreur, texte = détail + "Please read Help for more information." Boutons :
- "Help" : ouvre l'aide ;
- pour "No Internet connection" et "Failed to download hosts source" : "Retry sync" (relance une synchronisation) ;
- pour les autres erreurs : "Close".
Catalogue des erreurs en 15.1.

## 3. Accueil TV

### 3.1 Contrôles à l'ouverture

- Si l'assistant TV n'a jamais été terminé :
  - si le VPN n'a jamais été activé avec succès par l'utilisateur : ouverture de l'assistant TV, l'accueil se ferme ;
  - sinon (installation ancienne, ou assistant réussi mais quitté avant "Finish") : l'assistant est marqué comme terminé.
- Au premier affichage seulement : si la méthode est VPN et que l'autorisation VPN système manque, la demande système s'affiche. Si l'utilisateur accepte, l'app lance une activation complète (synchronisation des sources puis démarrage du blocage, comme dans l'assistant). S'il refuse, rien ne se passe.
- Au premier affichage : vérification de nouvelle version de l'app si "Check for update at startup" (app) est actif. Pas de vérification des sources au démarrage sur TV.

### 3.2 Mise en page

L'écran défile verticalement si la TV est petite ou le texte agrandi.

1. Bandeau d'en-tête rouge : logo, "AdAway Community", "Open Source ad blocker", et à droite "vX.Y.Z" (non cliquable).
2. Fine barre de progression indéterminée collée sous le bandeau, visible seulement pendant une opération.
3. Deux colonnes centrées verticalement.

Colonne gauche (état et actions) :
- Pastille d'état ronde posée sur la même vague que la barre du bas mobile : rouge pâle + icône pause si le blocage est appliqué, gris + logo sinon.
- Texte "Ad-blocking: enabled" ou "Ad-blocking: disabled".
- Petit texte d'état en direct (mêmes messages qu'en 2.3).
- Indicateur "Always-on VPN active" (vert, avec coche) : visible seulement si le système a AdAway comme VPN permanent. Rafraîchi à chaque retour sur l'écran et à chaque changement d'état du blocage.
- Bouton principal (focus par défaut à l'ouverture) : "Enable ad-blocking" (icône marche, fond gris) ou "Disable ad-blocking" (icône pause, fond rouge). Même effet que le bouton flottant mobile.
- Bouton "Install version X" (caché par défaut) : visible si une mise à jour est disponible ; ouvre l'écran de mise à jour.
- Rangée de deux cartes :
  - "VPN persistence" : visible uniquement en méthode VPN ; ouvre le dialogue de persistance VPN version TV (avec commandes ADB, voir 15.3) ;
  - bouton de thème : affiche "Light mode" si le thème actuel est sombre, "Dark mode" sinon. Un appui enregistre le mode choisi (clair ou sombre forcé, jamais "système") et l'écran se redessine aussitôt.

Colonne droite (statistiques et navigation) :
- Trois cartes compteurs (Bloqués, Autorisés, Redirigés), mêmes textes, icônes et règles qu'en 2.3 ; chacune ouvre l'écran des listes TV sur le type correspondant.
- Carte Sources : mêmes deux lignes qu'en mobile ; la carte ouvre l'écran des sources TV ; le bouton rond "synchroniser" intégré est focalisable séparément et lance une synchronisation.
- Trois tuiles : "DNS log" (ouvre le journal DNS TV), "Help" (aide commune), "Preferences" (préférences communes).

### 3.3 Navigation à la télécommande

- Focus initial sur le bouton principal.
- Droite depuis le bouton principal : première carte compteur. Gauche depuis la première carte compteur : bouton principal.
- Gauche/droite entre les trois cartes compteurs ; bas depuis une carte compteur : carte Sources ; droite depuis la carte Sources : bouton synchroniser ; bas depuis Sources ou synchroniser : tuile "DNS log".
- Gauche/droite entre les trois tuiles ; haut depuis une tuile : carte Sources.
- Bas depuis les tuiles et depuis le bouton principal : entrée de la rangée persistance/thème (carte persistance si visible, sinon bouton thème). Si le bouton "Install version X" est visible, le bas depuis le bouton principal va d'abord sur lui.
- Aucun déplacement ne doit rester bloqué quand un élément est caché (persistance en mode root, bannière absente) : les liens de focus se recalculent dès qu'un élément apparaît ou disparaît.

### 3.4 Erreurs

Même dialogue d'erreur que sur mobile (2.4), avec "Retry sync" pour les erreurs de connexion ou de téléchargement.

## 4. Assistant de premier lancement mobile

### 4.1 Structure commune

- Fond rouge, quatre étapes qui ne se balaient pas au doigt (passage uniquement par les boutons).
- En bas : quatre points d'étape (le point actif est plein et un peu plus gros, les autres en contour), un bouton "Back" à gauche et un bouton "Next" à droite (le dernier écran affiche "Finish").
- "Back" est caché sur la première étape. Le bouton Retour du système revient d'une étape (sur la première étape il quitte l'app normalement).
- "Next" est caché tant que l'étape ne l'autorise pas (sauf étape 2, voir plus bas).
- "Finish" ouvre l'accueil et ferme l'assistant.

### 4.2 Étape 1 : choix de la méthode

- Logo, titre "Welcome to AdAway!", texte "AdAway provides two ad blocking methods. Pick the one you like:".
- Deux cartes cliquables :
  - "Root based ad blocking" avec avantages "Faster", "Battery friendly" (icône plus) et inconvénient "Root required" (icône moins) ;
  - "VPN based ad blocking" avec inconvénients "Slower", "Run in background" et avantage "Compatibility".
- Appui sur Root : l'app demande l'accès root (la carte n'est plus cliquable pendant la demande).
  - accordé : la carte Root passe en vert, la méthode root est enregistrée, "Next" apparaît ;
  - refusé ou absent : la méthode est remise à "non choisie", "Next" disparaît, dialogue "Rooted Android required" : "Either the su binary could not be found or you did not allow root permission for AdAway. This can happen when your device is not rooted. You can find information about how to root your device on wiki.lineageos.org or other Android websites." Bouton "Close".
- Appui sur VPN : si l'autorisation VPN est déjà accordée, la carte VPN passe en vert, méthode VPN enregistrée, "Next" apparaît. Sinon la demande système s'affiche :
  - acceptée : même résultat ;
  - refusée : méthode remise à "non choisie", "Next" caché, puis dialogue "Always-on VPN enabled" si un autre VPN est réglé en VPN permanent ("AdAway detected that another VPN has the "Always-on VPN" setting active. This prevents AdAway from requesting VPN access. Please disable "Always-on VPN" on the correspondent VPN and try again."). Si le système interdit de lire ce réglage, variante "AdAway failed to request VPN access. This might be caused because another VPN has the "Always-on VPN" setting active...". Boutons "Close" et "Open "VPN" Settings" (ouvre les réglages VPN du système). Si aucun VPN permanent n'est détecté, pas de dialogue.
- Choisir une méthode désélectionne visuellement l'autre.

### 4.3 Étape 2 : synchronisation

- Dès l'affichage de l'étape, l'app lance l'activation : téléchargement des sources puis application du blocage avec la méthode choisie.
- Affichage : grand indicateur rond de chargement, titre "Syncing", texte "AdAway downloads ad networks to block from online sources. You can customize them later in the settings."
- "Next" est visible tout de suite. Un appui avant la fin ouvre le dialogue "Sync not finished yet" : "The block lists haven't finished downloading. You can continue now and it will keep going in the background, or wait here until it's done." Boutons "Wait" (reste) et "Continue anyway" (passe à l'étape suivante, la synchro continue).
- Réussite : titre "Synced!", l'indicateur est remplacé par une coche. Toutes les sources sont alors activées et, si l'une d'elles était désactivée, une nouvelle synchronisation est lancée.
- Échec : l'indicateur est remplacé par une icône "nuage barré", plus un texte "Failed to sync: <message court de l'erreur> Retry now?" et un bouton rond "réessayer". Un appui sur le texte ou le bouton relance l'activation.
- Notifications (Android 13 et plus, si la permission n'est pas accordée) : un texte s'affiche en bas : "It can send notifications to display ad block status and control, and to notify about available app update (a few per year). Enable them if you want to stay up-to-date." La demande système de permission est lancée une seule fois, soit à la réussite de la synchro, soit au bout de 10 secondes, au premier des deux.

### 4.4 Étape 3 : optimisation de la batterie

- Toujours autorisée à passer ("Next" visible), purement informative.
- Icône, titre "Stay protected in the background", texte "Android can pause AdAway's VPN in the background to save power, which can silently stop ad blocking. Allow AdAway to ignore battery optimization so it keeps running."
- Bouton "Allow" : ouvre la demande système d'exemption pour AdAway ; si elle n'existe pas sur l'appareil, ouvre la liste générale des optimisations de batterie ; si rien n'existe, rien ne se passe.
- Si l'exemption est déjà accordée (contrôlé à l'affichage, au retour du réglage et à chaque retour sur l'écran), le bouton devient "Allowed ✓" et est désactivé.
- Note de bas : "You can always change this later from" suivi du chemin "Android Settings › Apps › AdAway › Battery" (segments séparés par une petite icône chevron).

### 4.5 Étape 4 : gestionnaire de batterie du constructeur

- Toujours autorisée à passer, purement informative. Le bouton affiche "Finish".
- Titre "One more thing: your phone's own battery manager", texte "Some phone brands have their own battery manager that can also block AdAway. Here's what to check on yours:", avertissement "These are general pointers, not an exact guide: the wording and location can vary by phone model and Android version."
- Une carte de conseil adaptée à la marque détectée (fabricant ou marque de l'appareil contenant un des mots-clés, sans tenir compte de la casse) :
  - Samsung ("samsung") : "Samsung (One UI)", chemin "App info › Battery", conseil "Look for something like "Background usage limits" › "Never sleeping apps", then add AdAway to that list.", complément "Also check for an "Auto optimization" setting and turn off "Restart when needed" if present."
  - Xiaomi ("xiaomi", "redmi", "poco") : "Xiaomi / Redmi / POCO (MIUI / HyperOS)", chemin "App info › Battery saver", "Choose "No restrictions".", complément "Also enable "Autostart" ("Background autostart" on newer versions)."
  - Oppo ("oppo", "oneplus", "realme") : "Oppo / OnePlus / Realme (ColorOS)", chemin "App info › Battery usage", "Choose "Allow background activity" (sometimes shown as "No restrictions").", complément "Also look for an "Auto-launch" toggle for AdAway (often under Settings › Apps), so it can restart itself after being force-stopped."
  - Huawei ("huawei", "honor") : "Huawei / Honor (EMUI / MagicOS)", chemin "App info › Battery", "Look for "App launch", turn off "Manage automatically", then enable all 3: "Auto-launch", "Secondary launch" and "Run in background".", complément "Also lock AdAway in the Recents screen (swipe down on its card until a padlock appears)."
  - vivo ("vivo", "iqoo") : "vivo / iQOO (Funtouch / OriginOS)", chemin "App info › Battery usage", "Look for a background power option and choose "Don't restrict background power usage" (not "Smart control").", complément "Also look for an "Autostart" setting for AdAway, often under Settings › Applications."
  - autre marque : "Other brand", pas de chemin, "Look in app info for a battery or background activity setting and make sure AdAway is not restricted.", pas de complément.
- Bouton "Open app info" : ouvre la fiche système de l'app. En cas d'impossibilité, message court "Could not open the app info screen. Open it manually from Android Settings → Apps → AdAway."

### 4.6 Remarque

Les étapes 3 et 4 s'affichent aussi en méthode root, alors qu'elles ne concernent que le VPN (voir défauts).

## 5. Assistant de premier lancement TV

Trois pages, fond rouge, tout se fait par boutons. Le focus est placé automatiquement sur le bouton utile à chaque changement de page ou d'état.

1. Page d'accueil : logo, "AdAway Community", "Open Source ad blocker", "This short wizard turns on ad blocking on this device.", bouton "Get started" (focus initial).
2. Page méthode : "Choose your blocking method", "AdAway provides two ad blocking methods. Pick the one you like:", puis deux boutons empilés avec leur explication :
   - "Use root" / "Faster and battery-friendly. Requires a rooted device." (focus par défaut) : demande l'accès root (bouton désactivé pendant la demande). Accordé : méthode root enregistrée, passage direct à la page 3 en état "progression" avec lancement immédiat de l'activation. Refusé : dialogue "Rooted Android required" (même texte que mobile), on reste sur la page.
   - "Use VPN" / "Works without root, compatible with any Android TV device." : méthode VPN enregistrée, passage à la page 3 en état "invite".
3. Page activation, avec quatre états exclusifs :
   - Invite (VPN seulement) : icône clé, "Turn on ad blocking", "It's a local VPN: it filters ads on this device, without routing your traffic through an outside server.", bouton "Enable ad-blocking". Le bouton demande l'autorisation VPN si nécessaire (refus : on reste sur l'invite), puis lance l'activation.
   - Progression : indicateur rond, "Syncing", et le texte d'état en direct.
   - Réussite : coche, "Synced!", bouton "Finish" (marque l'assistant comme terminé et ouvre l'accueil TV).
   - Erreur : icône d'alerte, "Failed to sync: <message> Retry now?", bouton "Retry sync" (en VPN, repasse par la vérification d'autorisation ; en root, relance directement).
- Une fois une méthode choisie, impossible de revenir en arrière dans l'assistant. Le bouton Retour de la télécommande quitte l'assistant ; au prochain lancement, l'assistant revient tant qu'il n'a pas réussi.
- Pas d'étapes batterie sur TV, pas de demande de permission de notifications, pas de réactivation automatique de toutes les sources après la synchro (différence avec le mobile).

## 6. Écran des sources

### 6.1 Mobile

- Titre "Hosts sources", flèche retour.
- Liste de cartes, triées par nom (ordre alphabétique). La "liste perso" de l'utilisateur n'apparaît jamais ici.
- Chaque carte :
  - case à cocher "active" à gauche (un appui active/désactive la source) ;
  - nom de la source (gras), adresse (URL ou fichier) ;
  - état de mise à jour (voir 6.3) ;
  - nombre d'entrées à droite, par exemple "76k hosts" (vide si la source est désactivée ou vide).
  - Appui sur la carte : ouvre l'édition de la source.
- Bouton flottant "+" (libellé "Add source") : ouvre l'édition en mode ajout.
- Barre "appliquer" (voir 14) : configurée pour resynchroniser les sources avant d'appliquer.
- Pas d'état vide spécifique (liste vide si aucune source).

### 6.2 TV

- En-tête : icône marque-pages, titre "Hosts sources", boutons "Add" et "Apply" (caché au départ), puis sous les boutons le texte "Your configuration changed. You need to apply it." (caché au départ).
- Liste de cartes (nom, adresse, état, nombre). Une source désactivée est affichée à demi transparente.
- Liste vide : texte "No entries in this list yet."
- Appui sur une carte : dialogue titré par le nom de la source, avec les choix "Disable"/"Enable" (selon l'état) et "Edit", plus "Cancel".
- "Add" ouvre l'édition TV en mode ajout.
- "Apply" apparaît (avec le texte d'explication) dès que l'utilisateur a réellement modifié quelque chose sur cet écran : activation/désactivation, ou enregistrement/suppression confirmés depuis l'édition. Il n'apparaît pas pour une synchro faite en arrière-plan. Un appui le cache et lance l'application (avec resynchronisation des sources).
- Focus : droite depuis "Add" va sur "Apply" quand il est visible.

### 6.3 Texte d'état d'une source (mobile et TV)

- Source désactivée : "Disabled".
- Date de modification en ligne connue :
  - jamais installée localement : "Last update <durée> ago" ;
  - version en ligne plus récente que la locale : "Need update for <durée>" ;
  - sinon : "Up-to-date for <durée>".
- Date en ligne inconnue mais date locale connue : "Installed <durée> ago".
- Aucune date : "Unknown update status".
- Durée approximative : moins d'une heure "few minutes" ; puis "N hour(s)" ; puis "N day(s)" ; puis "N month(s)" (un mois = 30 jours).

### 6.4 Nombre d'entrées

- Afficher un format compact lisible : unités en dessous de 1 000, puis "k", "M", "G" avec arrondi. Ne pas reproduire l'ancien calcul (voir défauts).

## 7. Édition d'une source

### 7.1 Mobile

- Titre "Add source" (ajout) ou "Edit source" (édition), flèche retour (abandon sans enregistrer).
- Actions dans la barre de titre : "Delete" (icône corbeille, seulement en édition) et "Apply" (icône coche).
- Champs, de haut en bas :
  - "Label" : texte obligatoire.
  - "List format" : choix exclusif "Block" (par défaut) ou "Allow".
  - "Type" : choix exclusif "URL" (par défaut) ou "File". Il y a toujours un choix actif.
  - Si URL : champ "Location", prérempli avec "https://".
  - Si File : un texte cliquable remplace le champ, "Press File to select source file" tant qu'aucun fichier n'est choisi, puis l'adresse du fichier choisi. Choisir "File" ouvre tout de suite le sélecteur de fichiers du système (tout type de fichier) ; un appui sur le texte le rouvre. L'app garde l'accès en lecture au fichier choisi de façon permanente.
  - Case "Apply redirected hosts" et avertissement "Allowing redirected hosts may cause security issues. Only use this settings on a trusted source as it could redirect some sensitive traffic to whatever server it wants." Visibles seulement en format "Block" (apparition/disparition en fondu).
- Passer de File à URL remet le champ à "https://" ; passer de URL à File vide le champ.
- En édition, les champs sont préremplis avec la source existante.
- Validation à l'appui sur "Apply", avec message d'erreur sous le champ fautif :
  - nom vide : "Label required" ;
  - URL vide : "Location required" ;
  - URL invalide : "Invalid location". Est valide une adresse https:// complète (pas juste "https://"), ou une adresse de contenu fournie par le sélecteur de fichiers. Le http:// simple est refusé.
  - fichier non choisi : "Invalid location".
- Enregistrement : la source est créée active ; "Apply redirected hosts" n'est retenu qu'en format Block. L'écran se ferme.
- "Delete" : supprime la source immédiatement, sans confirmation, et ferme l'écran.

### 7.2 TV

- Même formulaire et mêmes règles, en une seule colonne défilante, ordre fixe de haut en bas (Label, List format, Type, Location ou fichier, case redirection, avertissement).
- En-tête : titre ("Add source" ou "Edit source") et boutons "Apply" (focus par défaut) et "Delete" (édition seulement).
- Dans le champ "Location", haut/bas changent toujours de champ en un seul appui ; gauche/droite déplacent le curseur ; le curseur est placé en fin de texte à l'arrivée dans le champ.
- Le résultat (enregistré ou supprimé) est remonté à l'écran des sources TV pour faire apparaître "Apply" ; un abandon (Retour) ne le fait pas apparaître.

## 8. Écran des listes (Bloqués, Autorisés, Redirigés)

### 8.1 Mobile

- Titre "Your lists", flèche retour. Ouvert sur l'onglet demandé (Bloqués par défaut).
- Barre de titre :
  - recherche (icône loupe, libellé "Filter hosts") : champ "Search hostname…", déplié, validé avec la touche "rechercher" du clavier. Le filtre garde les noms qui contiennent le texte saisi ; "*" vaut n'importe quelle suite de caractères et "?" un seul caractère. Le filtre s'applique aux trois onglets.
  - "Toggle sources" (icône marque-pages) : alterne entre "toutes les entrées (sources + perso)" (état par défaut) et "seulement mes entrées perso". Aucun indicateur ne montre l'état courant.
- Quand une recherche est active, le premier Retour efface la recherche au lieu de quitter l'écran.
- Pied de page rouge avec trois onglets à icônes : "Blocked", "Allowed", "Redirected", et un petit indicateur blanc animé sous l'onglet actif. On peut aussi balayer horizontalement entre les onglets. Changer d'onglet annule toute sélection en cours.
- Liste : triée par nom, chargée par pages au défilement (des dizaines de milliers d'entrées possibles), un nom n'apparaît qu'une fois même s'il vient de plusieurs sources.
  - Ligne : case à cocher "active" + nom ; dans "Redirected", deuxième ligne avec l'adresse IP cible.
  - Entrée perso : la case est active (appui = activer/désactiver). Appui long : la ligne se surligne et une barre d'action contextuelle s'ouvre, titre "Edit Entry", actions "Edit" et "Delete". Une seule sélection à la fois.
  - Entrée venant d'une source : case grisée (non modifiable). Appui long : copie le nom dans le presse-papiers, message "Host copied to clipboard".
- Bouton flottant "+" (libellé "Add host") : ouvre le dialogue d'ajout de l'onglet courant.
- Barre "appliquer" (voir 14) : configurée pour ne pas retélécharger les sources (recalcul local seulement).
- Pas d'état vide spécifique sur mobile.

### 8.2 Dialogues d'ajout et d'édition (mobile et TV)

- Bloqués : titre "Add host to blacklist" / "Edit blacklisted host", libellé "Hostname:", un champ texte (clavier sans suggestions). Valide seulement un nom de domaine correct, sans joker.
- Autorisés : titre "Add host to whitelist" / "Edit whitelisted host", libellé "Hostname:", un champ, note "(Wildcard characters * and ? are allowed)". Valide un nom correct, avec ou sans jokers "*" et "?".
- Redirigés : titre "Add host redirect" / "Edit redirect", champs "Hostname:" et "IP (IPv4 or IPv6):". Valide si le nom est correct (sans joker) et l'IP est une IPv4 ou IPv6 valide.
- Boutons : "Add" (ajout) ou "Save" (édition), et "Cancel". Le bouton de validation est grisé tant que la saisie est invalide (au départ grisé en ajout, actif en édition), et se met à jour à chaque frappe.
- En édition, champs préremplis, curseur en fin de nom.
- Ajout d'un nom déjà présent dans les entrées perso : l'entrée existante est remplacée (même si elle était d'un autre type) et réactivée.
- Toute modification (ajout, édition, activation, suppression) fait apparaître la proposition d'appliquer.

### 8.3 TV

- En-tête : ligne de titre non cliquable qui EST le type affiché (icône + "Blocked", "Allowed" ou "Redirected"). Pas d'onglets : on change de type depuis les cartes de l'accueil TV.
- Boutons d'en-tête dans l'ordre : "Add", "Search", "Toggle sources", "Apply" (caché au départ). Sous les boutons, "Your configuration changed. You need to apply it." quand "Apply" est visible.
- "Search" : dialogue "Filter hosts" avec un champ "Search hostname…" prérempli avec le filtre actif, boutons "Search" (applique), "Clear" (retire le filtre) et "Cancel".
- Liste : nom, et IP cible si elle existe. Une entrée désactivée est à demi transparente. Liste vide : "No entries in this list yet."
- Appui sur une entrée venant d'une source : copie directe du nom ("Host copied to clipboard"), pas de dialogue.
- Appui sur une entrée perso : dialogue titré par le nom avec "Disable"/"Enable", "Edit", "Delete", "Copy hostname", plus "Cancel". La suppression est immédiate, sans confirmation.
- "Apply" : se cache et lance l'application (sans retéléchargement des sources).
- Focus : gauche/droite entre les boutons d'en-tête, "Apply" accessible à droite de "Toggle sources" quand il est visible.

## 9. Journal DNS

### 9.1 Mobile

- Titre "DNS requests", flèche retour.
- Barre de titre : "Sort domains" (icône tri) et "Clear log" (icône corbeille).
- Texte d'aide quand la liste est vide : "Press record to start logging requests, browse the Web or use apps, then go back or swipe to refresh the logs." En méthode root, ajout : "Blocked requests will not be logged. Disable ad-blocking first if you want to log them too."
- Liste des noms de domaine demandés. Chaque ligne : le nom, puis trois icônes "bloquer", "autoriser", "rediriger". L'icône correspondant à l'état actuel du nom est colorée en rouge de marque.
  - Appui sur le nom : ouvre "http://<nom>" dans le navigateur.
  - Appui long sur le nom : copie dans le presse-papiers ("Host copied to clipboard").
  - Appui sur une icône non colorée : ajoute le nom à la liste perso de ce type. Pour "rediriger", dialogue "Redirect domain" avec un champ "IP (IPv4 or IPv6):", bouton "Add" grisé tant que l'IP est invalide, et "Cancel".
  - Appui sur l'icône colorée : retire le nom de la liste perso.
  - Après chaque ajout ou retrait, la ligne se met à jour tout de suite et la barre "appliquer" apparaît.
- Tri : par défaut "par domaine de premier niveau" (comparaison des morceaux du nom en partant de la fin : "com", puis "google", etc.). "Sort domains" alterne avec le tri alphabétique et affiche un message court "Alphabetical sort" ou "Top level domain sort".
- "Clear log" vide le journal immédiatement, sans confirmation.
- Tirer vers le bas rafraîchit la liste. Le rafraîchissement est aussi fait à chaque retour sur l'écran (avec l'indicateur de chargement).
- Bouton flottant "enregistrer" (libellé "Toggle log recording") : icône "enregistrer" à l'arrêt, "pause" pendant l'enregistrement. Un appui démarre ou arrête l'enregistrement.

### 9.2 TV

- En-tête : icône, titre "DNS requests", boutons "Refresh", bouton d'enregistrement et "Clear log".
- Bouton d'enregistrement :
  - VPN inactif : désactivé, libellé "Start Recording (VPN not active)" ;
  - sinon : "Start Recording" ou "Stop Recording".
  - Si le VPN s'arrête pendant un enregistrement, l'enregistrement est coupé automatiquement.
- Pendant l'enregistrement, la liste se rafraîchit toute seule chaque seconde (seulement quand l'écran est visible).
- Liste vide : même texte d'aide que mobile (sans le complément root).
- Ligne : icône d'état (interdit en rouge si bloqué, coche en vert si autorisé, cercle vide sinon) + nom.
- Appui sur une ligne : dialogue titré par le nom avec "Add to allowed" (ou "Remove from allowed" si autorisé), "Add to blocked" (ou "Remove from blocked" si bloqué), "Copy hostname", plus "Cancel".
- Pas de tri, pas de redirection, pas d'ouverture dans le navigateur sur TV.

## 10. Préférences

### 10.1 Écran principal (commun mobile et TV)

Titre "Preferences", flèche retour. Chaque réglage a une icône ; les lignes d'explication n'en ont pas mais restent alignées. Les titres longs passent à la ligne.

Section "General" :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Dark theme" | liste "Light" / "Dark" / "System default" | mobile : "System default" ; TV : "Dark" | Appliqué immédiatement (l'écran se redessine). |
| "Automatic updates" | lien | | Ouvre le sous-écran "Updates" (10.2). |

Section "VPN based ad blocker" (active seulement en méthode VPN, sinon toute la section est grisée et non focalisable) :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Enable at startup" | interrupteur | activé | Démarre le VPN au démarrage de l'appareil. |
| "Monitor connection" / "Periodically check the network state to restart VPN on disconnection." | interrupteur | désactivé | Surveillance périodique du réseau pour relancer le VPN. |
| Texte : "Configure which applications should not use the VPN so no connections will be blocked." | info | | Non cliquable. |
| "Exclude system applications" | liste "None" / "All except browsers" / "All" | "None" | Le choix actuel s'affiche en résumé. Un changement redémarre le VPN s'il tourne. |
| "Exclude user applications" | lien | aucune app | Ouvre l'écran "Excluded applications" (10.4). Au retour, redémarre le VPN s'il tourne. |
| "Reset VPN" / "Rebuild the VPN connection from scratch. Use this if the VPN stops working (no internet access or DNS errors)." | action | | VPN actif : redémarrage + message "VPN connection rebuilt." ; sinon message "VPN is not running." |
| "Record diagnostic log" / "Turn on before reproducing a problem, then export it below. May slightly slow down older devices. Records only technical VPN events, never browsing history or personal data." | interrupteur | désactivé | Effet immédiat. |
| "Diagnostic log" / "View, copy or share the recent errors and VPN events. Useful when reporting a problem." | action | | Ouvre le dialogue du journal (15.4). |
| "Clear diagnostic log" / "Delete the recorded events. Do this before reproducing a problem to capture a clean trace." | action | | Efface sans confirmation, message "Diagnostic log cleared." |

Section "Root based ad blocker" (active seulement en méthode root, sinon grisée et non focalisable) :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Open hosts file" | action | | Ouvre le fichier hosts système dans un éditeur de texte externe (en rendant la partition modifiable si besoin, puis en la remettant en lecture seule au retour). Sans éditeur : dialogue "No text editor installed" (15.2). |
| "Hide reboot dialog after apply" | interrupteur | désactivé | Sans effet aujourd'hui (voir défauts). |
| Texte : "Define where to redirect blocked hosts" | info | | |
| "Configure IPv4 redirection" | champ texte | "127.0.0.1" | Doit être une IPv4 valide, sinon refus et message "Invalid redirection". |
| "Configure IPv6 redirection" | champ texte | "::1" | Doit être une IPv6 valide, sinon "Invalid redirection". Grisé si "Enable IPv6 support" est coupé. |
| Texte : "The local web server listens on local IP addresses to answer to blocked hostname requests. It may help with app freezing on blocked connection." | info | | |
| "Enable web server" | interrupteur | désactivé | Démarre ou arrête le serveur web local. Si au bout d'une demi-seconde l'état réel ne correspond pas, l'interrupteur se recale tout seul. |
| "Test web server" | action, dépend du serveur web | | Résumé = état : "Checking…", puis "Not running", "Running but certificate not installed" ou "Running and certificate installed". Contrôle à l'ouverture et à chaque retour (en méthode root seulement). Un appui ouvre l'adresse de test du serveur dans le navigateur. |
| "Install self-signed certificate" | action, dépend du serveur web | | Avant Android 11 : installation directe. Android 11 et plus : enregistrement du certificat dans un fichier choisi ("adaway-webserver-certificate.crt"), puis dialogue "Certificate manual install" avec le mode d'emploi et le bouton "Open "Security" settings". |
| "Replace blank ad space by app icon" | interrupteur, dépend du serveur web | activé | Un changement redémarre le serveur web s'il tourne. |

"Dépend du serveur web" : grisé tant que "Enable web server" est coupé.

Section "Ad blocking" :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Enable IPv6 support" | interrupteur | désactivé | Pris en compte par les deux méthodes. |
| "Backup / restore block rules" | lien | | Ouvre "Backup & restore" (10.3). |

Section "Debug" (visible uniquement dans une version de développement) :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Verbose logging" / "App restart is needed to apply" | interrupteur | désactivé | Journalisation détaillée après redémarrage. |

### 10.2 Sous-écran "Updates"

Titre "Updates".
- "Notifications are disabled" / "Application notifications won't show. Tap to enable them." : visible seulement sur Android 13 et plus quand la permission de notifications n'est pas accordée (vérifié à chaque retour). Ouvre les réglages de notifications de l'app.

Section "Application updates" :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Check for update at startup" | interrupteur | activé | Vérifie une nouvelle version à l'ouverture de l'accueil. |
| "Periodically check for update" | interrupteur | activé | Active/coupe la vérification périodique en arrière-plan. |
| "Check now" | action | | Grisé pendant la vérification, puis message "New version available: X" ou "No update available". |
| "Update now" / "Install version X" | action | | Visible seulement si une mise à jour est connue. Ouvre l'écran de mise à jour. |

Section "Hosts updates" :
| Réglage | Type | Défaut | Comportement |
|---|---|---|---|
| "Check for update at startup" | interrupteur | activé | Vérifie les sources à l'ouverture de l'accueil mobile. |
| "Periodically check for update" | interrupteur | activé | Active/coupe la vérification périodique des sources. |
| "Sync on update" | interrupteur, dépend du précédent | activé | Télécharge et applique automatiquement quand une source a changé. |
| "Sync on unmetered network only" | interrupteur, dépend du précédent | désactivé | Limite la tâche périodique aux réseaux non facturés (Wi-Fi). |

### 10.3 Sous-écran "Backup & restore"

- "Backup" / "Backup to external storage your block rules" : ouvre le sélecteur "créer un fichier" avec le nom proposé "adaway-backup.json", puis exporte. Message long "Backup successfully exported to file 'X' on your external storage." ou "Failed to export backup file 'X'."
- "Restore" / "Restore your block rules from backup file" : ouvre le sélecteur de fichier (JSON ; sur les vieux Android, types plus larges), puis importe. Message "Backup successfully imported from your external storage." ou "Failed to import backup. Is its format correct? Check logcat for more details."
- Le contenu exact de la sauvegarde est décrit dans une autre zone. Remarque fonctionnelle : une source de type fichier restaurée perd son autorisation d'accès.

### 10.4 Écran "Excluded applications"

- Titre "Excluded applications", flèche retour. Menu de débordement : "Select all", "Deselect all".
- Liste des applications installées par l'utilisateur (pas les apps système, pas AdAway), triées par nom. Chaque ligne : icône, nom, nom de paquet, interrupteur "exclue". Un appui sur la ligne bascule l'interrupteur.
- Chaque changement est enregistré tout de suite. Le VPN est redémarré au retour sur les préférences.

## 11. Aide et À propos

- Titre "Help", flèche retour. Trois onglets : "FAQ", "Problems", "About". Balayage possible entre onglets.
- FAQ (texte riche défilant, liens cliquables sur mobile) : sections "What is AdAway Community?", "How does it block ads?" (VPN par défaut sans root, ou root via fichier hosts, module "systemless hosts" requis sur Android récent), "How do I block, allow or redirect a hostname?", "Where can I find more hosts sources?" (lien vers la liste communautaire des sources), "Why does this fork exist?", "How do I report a bug or suggest a change?" (lien vers le suivi des tickets du fork).
- Problems : "The VPN turns off by itself" (batterie, démarrage automatique, verrouillage dans les apps récentes, "Enable at startup"), "No internet or slow browsing right after switching between Wi-Fi and mobile" (Reset VPN), "How do I export a diagnostic log?", "Chrome still shows ads" (DNS sécurisé de Chrome / DNS privé), "Some ads still get through, or an app still shows ads", "An app stopped working after enabling AdAway" (lien vers la liste des apps problématiques), "Applying the hosts file fails (root mode)", "Still stuck?".
- About : logo, "AdAway Community", "Version X", "Unofficial community-maintained fork of AdAway.", séparateur, quatre liens ("Source code (GitHub)", "Report an issue", "Releases", "Official AdAway upstream") qui ouvrent le navigateur, séparateur, "License: GNU General Public License v3.0 or later (GPLv3+)".
- Les contenus d'aide existent en plusieurs langues (dont anglais et français).
- TV : focus initial sur le premier onglet ; gauche/droite change d'onglet et le sélectionne aussitôt ; haut reste sur la rangée d'onglets ; bas entre dans le contenu ; haut quand le contenu est tout en haut renvoie sur l'onglet. Sur TV les liens ne sont pas focalisables (lecture seule, défilement simple).

## 12. Écran de mise à jour de l'app

- Fond rouge, sans barre de titre. Logo, titre, bouton, progression, puis zone défilante "Last changes" avec le journal des modifications (mis en forme à partir de Markdown).
- États :
  - à jour : titre "You are up-to-date", pas de bouton, notes de la version connue ;
  - mise à jour disponible : titre "Update available", bouton "Update now", notes de la nouvelle version ;
  - téléchargement : bouton caché, barre de progression en pourcentage et texte "X / Y" (tailles lisibles, par exemple "3,2 Mo / 12 Mo"), puis "Complete" ;
  - téléchargement fini : l'APK est vérifié puis l'installeur système s'ouvre.
- "Update now" retélécharge toujours un fichier neuf (jamais un fichier déjà présent).
- Si l'autorisation "installer des applications inconnues" manque : l'app ouvre ce réglage, mémorise qu'une installation est en attente et la reprend au retour (depuis cet écran ou depuis l'accueil mobile ou TV, même si l'app a été tuée entre-temps). Si l'autorisation n'a pas été donnée, la progression est masquée pour permettre de réessayer.
- Après l'installation, une notification "AdAway updated" / "Relaunch the app" s'affiche (zone notifications).

## 13. Scanner d'adwares

- Il existe un écran complet dans l'ancien code, mais aucun menu ni bouton n'y mène : il est inaccessible.
- Comportement prévu : titre "Adware", explication ("Here you can find installed adware, bad applications which can't be blocked by AdAway..."), "Scanning…" pendant l'analyse, "No Adware were found!" si rien, sinon une liste (nom de l'app, nom de paquet) triée par nom. Un appui lance la désinstallation système de l'app.
- Détection : une app est suspecte si un de ses composants (écran, récepteur, service) appartient à un SDK publicitaire connu (Airpush, AdNotify, AppBucks, Appenda, AppLovin, IAC notification, InMobi, Leadbolt, SellARing, SendDroid, Tapjoy, Kuguo).
- Voir questions ouvertes.

## 14. Barre "appliquer"

Rôle : modifier une liste ou une source ne change que la base de règles ; le blocage réel ne change qu'après "Apply".

- Mobile (écrans Sources, Listes, Journal DNS) : bandeau en bas d'écran, permanent jusqu'à action, texte "Your configuration changed. You need to apply it." et action "Apply".
  - Ne s'affiche pas au premier chargement de l'écran, seulement après un changement.
  - Un appui remplace le bandeau par "Applying new configuration…" avec un indicateur de chargement.
  - Sources : resynchronise les sources puis réapplique. Listes et Journal : recalcule localement puis réapplique.
  - Le blocage n'est réappliqué que s'il est actif. S'il est coupé, la base est mise à jour mais rien n'est réactivé.
  - Échec : bandeau court "Failed to apply new configuration." avec une icône d'erreur.
  - Changement fait pendant l'application : selon l'écran, nouveau bandeau à la fin (Listes, Journal) ou ignoré (Sources).
- TV (Sources, Listes) : bouton "Apply" dans l'en-tête + texte d'explication, car un bandeau n'est pas atteignable à la télécommande. Même logique d'application.
- Journal DNS TV : aucune proposition d'appliquer (voir défauts).

## 15. Dialogues (catalogue)

### 15.1 Erreurs d'opération (titre / détail)

- "No Internet connection" / "Unable to establish a connection to the Internet. Please check your device connectivity."
- "Failed to download hosts source" / "None of your enabled hosts sources are reachable. Please check you are properly connected to the Internet."
- "Failed to create private file" / "Unable to create a private file to build a new hosts source. Please check the free space available on your device."
- "Not enough space" / "Unable to copy hosts file to your system partition. Please check Magisk "systemless hosts" module is enabled then reboot."
- "Failed to install new hosts file" / "Unable to copy hosts file to /system partition. Please check Magisk "systemless hosts" module is enabled then reboot."
- "Failed to revert hosts file" / "Unable to restore default hosts file configuration."
- "Failed to enable VPN ad blocking." / "Please check your VPN settings to authorize the application VPN to start."
- "Failed to disable VPN ad blocking." / "Please check your VPN settings to disable it manually."

### 15.2 App manquante

- "No text editor installed" / "No text editor could be found to open the hosts file. You can install Jota or other text editors to handle this. Do you want to install Jota?" Boutons "Yes" (ouvre la boutique d'apps sur l'éditeur, sinon une recherche F-Droid, sinon rien) et "No".
- Variante "No file manager installed" (OI File Manager) existe mais n'est utilisée nulle part.

### 15.3 Persistance VPN

- Titre "VPN persistence".
- Texte : d'abord l'état ("Always-on VPN is enabled for AdAway. ✓" ou "Always-on VPN not set. AdAway's built-in persistence handles it."), puis "AdAway already handles reboots, low-memory kills and crashes on its own, so no extra setup is needed in most cases. If the VPN ever stops unexpectedly, pin AdAway as the Android system Always-on VPN for the strongest guarantee."
- Version TV seulement : ajout des commandes ADB pour activer ou désactiver le VPN permanent (avec le nom de paquet réel de l'app).
- Boutons "Open VPN settings" et "Got it".
- Si les réglages VPN ne s'ouvrent pas : message long, mobile "VPN settings are not available on this device.", TV "VPN settings hidden on this Android TV. Use the ADB commands shown." Sur TV le dialogue reste ouvert pour garder les commandes lisibles ; sur mobile il se ferme.

### 15.4 Journal de diagnostic

- Titre "Diagnostic log". Zone de texte défilante de hauteur fixe (les boutons restent toujours visibles).
- Vide : "No events have been recorded yet. Turn on "Record diagnostic log" above, then try to reproduce the problem." et un seul bouton "OK".
- Non vide : aperçu limité à la fin du journal (environ 20 000 caractères, précédé de "…"), boutons "Share" (partage du journal complet, titre "Share diagnostic log"), "Copy" (copie du journal complet, message "Diagnostic log copied to clipboard.") et "OK".

### 15.5 Autres

- Choix de méthode refusé, VPN permanent concurrent, synchro pas finie : voir 4.2, 4.3.
- Certificat : voir 10.1.
- Dialogues d'ajout/édition d'entrée et de redirection : voir 8.2, 9.1.
- Dialogues d'actions TV (source, entrée, journal) : voir 6.2, 8.3, 9.2.

## 16. Thème clair/sombre

- Trois modes : clair, sombre, suivre le système. Défaut : suivre le système sur mobile, sombre sur TV (les TV n'ont généralement pas de mode système).
- Le mode choisi s'applique à tous les écrans dès leur ouverture et immédiatement après un changement.
- Sur TV, le bouton de thème de l'accueil ne propose que clair ou sombre.
- Exceptions : assistant de premier lancement et écran de mise à jour toujours rouges.
- En thème sombre : fonds très sombres, rouge de marque un peu plus foncé, pastels adaptés. La barre de navigation système doit avoir des icônes lisibles dans les deux thèmes.

## 17. Spécificités TV (récapitulatif)

- Détection par le mode "télévision" du système. L'accueil mobile redirige vers l'accueil TV ; l'aide et l'à-propos adaptent le focus ; les préférences rendent les lignes grisées non focalisables pour que la télécommande les saute.
- Écrans dédiés : accueil, assistant (3 pages), sources, édition de source, listes, journal DNS. Principes : tous les contrôles dans un en-tête fixe en haut, un appui = un dialogue d'actions (pas d'appui long, pas de case à cocher, pas de bouton flottant, pas de bandeau), focus placé automatiquement sur le bon bouton après chaque changement d'état, liens de focus explicites recalculés quand un élément apparaît ou disparaît.
- Éléments propres à la TV : bouton de thème sur l'accueil, indicateur "Always-on VPN active", commandes ADB dans le dialogue de persistance.
- Bannière Leanback 320 x 180 : fond rouge de marque, logo blanc.
- L'écran des listes mobile reste utilisable au clavier/télécommande (haut depuis les onglets va au bouton "+", gauche/haut depuis "+" va à la dernière ligne visible, droite depuis une ligne va au "+").

## 18. Défauts ou bizarreries constatés (à ne pas reproduire)

- Accueil mobile : la demande d'autorisation VPN est relancée à chaque retour sur l'accueil si l'utilisateur l'a refusée, et son résultat est ignoré : boucle agaçante sans explication.
- Accueil mobile : la vérification des sources au démarrage est lancée même quand l'app part tout de suite vers l'assistant.
- Compteur "Blocked" : il compte aussi les noms qui sont par ailleurs autorisés, donc il surestime ce qui est réellement bloqué. Nombres affichés sans séparateur de milliers.
- Accueil : le dernier texte d'état reste affiché indéfiniment après une opération réussie.
- Nombre d'entrées d'une source : le calcul compte mal les chiffres (1 000 à 9 999 affichés en brut, 1 000 000 affiché "1000k", plafond absurde au-delà des milliards). Le calcul est dupliqué mobile/TV.
- Écran des sources mobile : la barre "appliquer" est déclenchée par n'importe quelle écriture en base, y compris une synchro d'arrière-plan que l'utilisateur n'a pas demandée (corrigé sur TV seulement).
- Édition de source : enregistrer une modification supprime puis recrée la source, ce qui efface ses dates, sa taille et force un nouveau téléchargement. Si la nouvelle adresse est déjà utilisée par une autre source, la source modifiée est perdue sans message. Ajouter une source avec une adresse déjà existante échoue en silence.
- Édition de source : "Delete" sans confirmation. Passer d'URL à File puis revenir efface l'URL saisie. Une source introuvable (identifiant invalide) donne un formulaire vide titré "Edit source".
- Le texte "URL: (a https:// or file:// resource)" annonce file:// alors que seuls https:// et les fichiers choisis via le sélecteur sont acceptés. Le http:// est refusé sans explication particulière.
- Dialogue "Autorisés" : le champ affiche l'indice "The hosts source URL", hors sujet.
- Le dialogue Bloqués n'accepte pas les jokers alors que la recherche et les Autorisés les acceptent : incohérence à clarifier (voir questions).
- Ajouter un nom perso existant change silencieusement son type (un nom bloqué devient autorisé). À l'inverse, depuis le journal DNS l'ajout crée un doublon au lieu de remplacer, et "retirer" supprime toutes les entrées perso du nom quel que soit le type.
- Journal DNS : retirer un nom qui est bloqué par une source (et non par la liste perso) n'a aucun effet réel, alors que l'icône se décolore comme si c'était fait.
- Journal DNS mobile : appui sur le nom ouvre "http://..." sans protection (plantage si aucun navigateur) ; "Clear log" sans confirmation ; aucune indication si le VPN est coupé.
- Journal DNS TV : aucune proposition d'appliquer après un ajout ou retrait, donc le changement n'est pas pris en compte tant que rien d'autre ne l'applique. L'enregistrement est impossible dès que le VPN n'est pas actif, donc en méthode root sur TV le journal est inutilisable. Pas de tri ni de redirection. Rafraîchissement forcé chaque seconde.
- Actions TV (suppression d'entrée, suppression de source) sans confirmation.
- Listes mobile : "Toggle sources" ne montre jamais l'état actif ; pas d'état vide ; pas de message quand le filtre ne trouve rien.
- Barre "appliquer" : si le blocage est coupé, l'appui sur "Apply" disparaît sans rien dire (rien n'est appliqué).
- Assistant mobile : les étapes batterie et constructeur s'affichent aussi en méthode root, où elles ne servent à rien. La synchro démarre à la création de l'étape 2 et n'est pas relancée si l'utilisateur revient changer de méthode : comportement ambigu. Revenir en arrière réaffiche "Next" sans vérifier qu'une méthode est toujours choisie.
- Assistant mobile : après la première synchro, toutes les sources sont réactivées d'office, y compris celles que l'utilisateur aurait désactivées avant (cas d'une réinstallation de données).
- Assistant TV : différences non justifiées avec le mobile (pas de permission de notifications, pas de réactivation des sources). Quitter avec Retour après une activation root réussie mais sans "Finish" relance l'assistant au prochain démarrage.
- Préférences : "Hide reboot dialog after apply" n'a aucun effet (il n'existe plus de dialogue de redémarrage).
- Préférences : l'état grisé de "Configure IPv6 redirection" n'est calculé qu'à l'ouverture de l'écran ; activer "Enable IPv6 support" sur le même écran ne le dégrise pas.
- Préférences : "Check now" affiche "No update available" même quand la vérification a échoué (pas de réseau).
- Préférences : le partage d'un très gros journal de diagnostic passe tout le texte en une fois, ce qui peut échouer sur Android.
- Applications exclues : liste chargée d'un bloc (lent avec beaucoup d'apps), sans recherche, tri sensible à la casse.
- Sauvegarde : le message de réussite affiche une adresse technique illisible au lieu du nom du fichier. Le texte d'échec renvoie à "logcat", incompréhensible pour un utilisateur.
- Mise à jour : si le téléchargement échoue, l'écran reste figé (bouton caché, barre affichée) sans message. Si aucune vérification n'a encore été faite, l'écran affiche "You are up-to-date" sans rien vérifier.
- Aide : le texte parle de "Preferences, VPN, Reset VPN" alors qu'il n'y a plus de sous-écran VPN. Les liens de l'à-propos s'ouvrent sans protection si aucun navigateur n'existe. Sur TV, les liens de l'aide sont inaccessibles.
- Tuile des réglages rapides : un échec (par exemple autorisation VPN manquante) ne donne aucun retour à l'utilisateur.
- Focus TV : les cartes de l'accueil TV n'ont pas d'état "focalisé" clairement marqué dans leur style (bordure fixe) ; le repère visuel dépend de l'effet par défaut du système.
- Scanner d'adwares : code complet mais écran inaccessible.
- Beaucoup de textes et de mises en page orphelins (anciens tiroirs de navigation, dialogue de redémarrage, ancien dialogue d'ajout de source, textes "tcpdump", boutons TV supprimés) : ne pas les reprendre.

## 19. Questions ouvertes

- Faut-il garder le scanner d'adwares (et lui donner une entrée dans l'interface) ou l'abandonner ?
- Faut-il supprimer le réglage "Hide reboot dialog after apply", ou réintroduire un dialogue "redémarrer maintenant ?" après application en root ?
- Le compteur "Blocked" doit-il montrer les noms réellement bloqués (bloqués moins autorisés) ?
- Les entrées bloquées perso doivent-elles accepter les jokers comme les autorisées ?
- Ajouter un nom déjà présent dans un autre type perso : remplacer, refuser avec message, ou garder les deux ? Il faut une règle unique pour les listes et le journal.
- Retirer depuis le journal un nom bloqué par une source : proposer plutôt "Autoriser" ?
- Faut-il des confirmations avant suppression (source, entrée, journal DNS, journal de diagnostic) ?
- L'assistant doit-il sauter les étapes batterie et constructeur en méthode root ?
- Faut-il aligner l'assistant TV sur le mobile (permission de notifications, retour en arrière possible) ?
- La barre "appliquer" doit-elle aussi réactiver le blocage, ou au moins dire qu'il est coupé ?
- Le journal DNS sur TV doit-il fonctionner en méthode root, proposer d'appliquer, le tri et la redirection ?
- Le bouton de thème TV doit-il proposer aussi "suivre le système" ?
- Accepter http:// (non chiffré) pour les sources, avec avertissement, ou continuer à le refuser ?
- L'écran de mise à jour doit-il lancer lui-même une vérification à l'ouverture, et afficher un état "vérification impossible" ?
- Faut-il un indicateur visible de l'état "Toggle sources" et un état vide sur les listes mobiles ?
- Faut-il garder l'ouverture d'un domaine du journal dans le navigateur (en http://) ?

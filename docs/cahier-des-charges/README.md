# Cahier des charges : réécriture d'AdAway Community en Kotlin et Rust

Ce dossier est la référence de la réécriture. Le code sera écrit à partir de ces documents, jamais à partir du code de la branche `main`.

## 1. But

Refaire entièrement AdAway Community, sans reprendre une seule ligne de l'ancien code (Java et C) :

- **Mêmes fonctions** que la version 6.6.1-c.
- **Sans ses bugs** : chaque chapitre liste les défauts constatés à ne pas reproduire.
- **Avec une architecture propre.**
- **Par-dessus l'ancienne app** : la nouvelle s'installe comme une mise à jour et l'utilisateur ne perd rien.

## 2. Règles du chantier (déjà décidées)

| Sujet | Décision |
|---|---|
| Branche | Tout le travail se fait sur `adaway-rust`. La branche `main` n'est jamais modifiée. |
| Rôle de `main` | Référence fonctionnelle uniquement : savoir ce que fait l'app, jamais copier son code. Seule exception : le chapitre 06, qui décrit les anciennes données pour pouvoir les relire. |
| Langages | Kotlin pour l'app, Rust pour le moteur et les outils root. Aucune ligne de C. |
| Interface | Jetpack Compose, mobile et Android TV. |
| Installation | Mise à jour par-dessus l'ancienne app : même identifiant `org.adaway`, même clé de signature, reprise des sources, listes perso et réglages. |
| Fonctions | Toutes gardées, sauf le scanner d'adwares (retiré) et les décisions de la section 6. |
| Android minimum | Android 8.0 (API 26). |
| Textes | Tous passent par les traductions, anglais d'abord, français ensuite. Les autres langues seront ajoutées plus tard. |
| Journaux de debug | Uniquement dans la version debug, jamais en release, beta ou autre. |
| Version release | Les règles de R8 sont vérifiées pour chaque bibliothèque : la version release doit marcher, pas seulement la debug. |

## 3. Comment lire ce cahier

| Chapitre | Contenu |
|---|---|
| [01-sources-listes.md](01-sources-listes.md) | Sources de listes, formats lus, listes perso, calcul de la liste finale, mises à jour automatiques, bouton "Appliquer". |
| [02-vpn.md](02-vpn.md) | Mode VPN : tunnel, traitement DNS, réseau, apps exclues, surveillance, notification, une quarantaine de situations délicates. |
| [03-root.md](03-root.md) | Mode root : fichier hosts, retour à l'origine, serveur web local, certificat, journal DNS root. |
| [04-interface.md](04-interface.md) | Tous les écrans mobile et TV, réglages complets, navigation à la télécommande, thème. |
| [05-annexes.md](05-annexes.md) | Mise à jour de l'app, sauvegarde, commandes externes, tuile, démarrage, notifications, journal de diagnostic, batterie. |
| [06-donnees-compat.md](06-donnees-compat.md) | Données laissées par l'ancienne app et tout ce qu'il faut garder identique pour la mise à jour par-dessus. |
| [07-pieges.md](07-pieges.md) | Environ 120 pièges tirés de l'historique du projet, chacun avec l'exigence qui l'évite. |

Les chapitres 01 à 05 décrivent le comportement **actuel**. Le comportement **attendu** est ce comportement :

- **corrigé** de tous les points de la section "Défauts ou bizarreries constatés" de chaque chapitre ;
- **complété** par les exigences du chapitre 07 ;
- **ajusté** par les décisions de la section 6 ci-dessous.

Les textes anglais et français cités dans les chapitres servent de base. Ils peuvent être améliorés, mais pas perdus.

## 4. Architecture cible

### 4.1 Qui fait quoi

| Partie | Langage | Rôle |
|---|---|---|
| Interface mobile et TV | Kotlin (Compose) | Tous les écrans, l'assistant, les réglages. |
| Intégration Android | Kotlin | Service VPN, notifications, démarrage au boot, tuile, tâches de fond, permissions, commandes externes, mise à jour de l'app. |
| Données de l'utilisateur | Kotlin | Sources, listes perso, réglages, reprise des anciennes données. |
| Téléchargements | Kotlin | Récupération des listes et de la mise à jour de l'app (cache HTTP, ETag, dates). |
| Moteur de listes | Rust | Lecture des fichiers de listes, calcul de la liste finale, index compact pour savoir instantanément si un domaine est bloqué, recherche paginée pour l'écran des listes. |
| Moteur DNS du VPN | Rust | Lecture des paquets du tunnel, réponse locale aux noms bloqués ou redirigés, relais vers le vrai serveur DNS, journal DNS borné. Conçu pour accueillir plus tard le DNS chiffré. |
| Fichier hosts | Rust | Génération du fichier hosts du mode root à partir de la même liste finale. |
| Serveur web local (root) | Rust | Programme séparé lancé en root : HTTP et HTTPS sur l'adresse locale. |
| Capture DNS (root) | Rust | Programme séparé lancé en root : lit les requêtes DNS pour le journal du mode root. |

Le même code Rust (lecture des listes, lecture des paquets DNS) sert à la fois à l'app et aux deux programmes root. Il n'est jamais écrit deux fois.

### 4.2 Données

- **L'app Kotlin garde ce qui appartient à l'utilisateur** : les sources, les listes perso et les réglages. Elle utilise un nouveau stockage, propre.
- **Le moteur Rust garde les listes téléchargées** dans son propre format compact. Ces données se régénèrent toujours en resynchronisant.
- **L'ancienne base (`app.db`) et l'ancien fichier de réglages (`preferences`) sont lus une seule fois**, à la première ouverture, puis convertis (voir chapitre 06, "Plan de reprise"). L'ancien format n'est jamais réutilisé tel quel.

## 5. Exigences communes à toute l'app

1. **Envie de l'utilisateur et état réel sont deux choses distinctes.** Aucun automatisme ne rallume un blocage que l'utilisateur a coupé, que ce soit une mise à jour, une synchronisation, une surveillance, Android ou un changement de réseau.
2. **Toute entrée venue de l'extérieur est considérée comme hostile** : paquets du tunnel, fichiers de listes, sauvegardes, réponses réseau. Une entrée invalide est ignorée sans rien faire tomber. Le moteur DNS est testé par fuzzing.
3. **Mémoire et durée bornées.** Une liste de plusieurs millions de lignes passe sans charger le fichier en entier. Chaque opération réseau a un délai maximal.
4. **Rien ne bloque l'interface** : root, réseau, disque et listes des apps tournent toujours en arrière-plan.
5. **Une seule vérité pour l'état affiché.** L'accueil, la tuile, la notification et l'icône VPN d'Android disent toujours la même chose, et la vérité.
6. **Aucune erreur silencieuse.** Chaque erreur a son message exact, et l'échec d'une seule source est visible. Une action sans effet, par exemple "Appliquer" alors que le blocage est coupé, le dit.
7. **Mobile et TV ont les mêmes fonctions.** Chaque écran est conçu dès le départ dans ses deux versions, et la version TV se pilote entièrement à la télécommande.
8. **Confidentialité.** Rien ne quitte l'appareil tout seul, il n'y a pas de télémétrie, et le journal de diagnostic ne contient jamais de nom de domaine visité.
9. **Rotation, changement de thème ou de langue : rien ne se perd et rien ne plante.**
10. **Des tests à chaque niveau** :
    - le moteur Rust (lecture, calcul de la liste finale, DNS) est couvert par des tests ;
    - la logique "envie / état" se teste sans Android ;
    - la reprise des anciennes données est testée sur une vraie base 6.6.1-c ;
    - chaque version publiée est testée en release sur un vrai appareil.
11. **Programmes natifs alignés sur 16 Ko** (exigence d'Android 15 et plus) et extraits à l'installation.
12. **Sécurité.**
    - Toute valeur écrite dans le fichier hosts est validée.
    - Un APK de mise à jour n'est installé qu'après vérification de son paquet et de sa signature.
    - Aucun secret partagé n'est livré dans l'app (le certificat du serveur web est généré sur l'appareil).

## 6. Décisions

Toutes les recommandations ci-dessous ont été validées le 2026-10-14. Les questions mineures des chapitres qui ne sont pas reprises ici seront tranchées selon la recommandation du chapitre, ou à défaut dans le sens qui corrige le défaut constaté.

### 6.1 Reprise et compatibilité

| N° | Question | Décision |
|---|---|---|
| A1 | Quelles anciennes bases relire ? | **Seulement la version 7.** Toutes les installations de la version Community sont en version 7, et les autres ne peuvent pas recevoir la mise à jour par-dessus (signature différente). Si la base est d'une autre version ou illisible : on garde quand même les réglages, on repart des sources par défaut et on propose de restaurer une sauvegarde. |
| A2 | Permettre de revenir à l'ancienne version ? | **Non.** Les données sont converties une fois pour toutes. |
| A3 | Garder les noms techniques visibles de l'extérieur (icône d'accueil, icône TV, tuile, raccourcis, commande Tasker, permission) ? | **Oui, à l'identique.** Sinon la tuile posée, les raccourcis et les automatisations des utilisateurs disparaissent. |
| A4 | Numéro de la première version | **7.0.0-c** (code 70000). |
| A5 | Relancer le blocage tout de suite après la mise à jour de l'app ? | **Oui**, pour le VPN comme pour le serveur web root, si l'utilisateur les avait allumés. |

### 6.2 Sources et listes

| N° | Question | Décision |
|---|---|---|
| B1 | Sources par défaut (nouvelles installations) | **StevenBlack activée, Peter Lowe proposée mais désactivée, AdAway retirée.** La liste AdAway n'est plus mise à jour depuis fin 2025, et StevenBlack reprend déjà AdAway et Peter Lowe. Les sources des utilisateurs existants ne sont jamais touchées. |
| B2 | Lire aussi les listes "un domaine par ligne" et le format Adblock simple (`\|\|domaine^`) ? | **Oui, les deux.** Ce sont des formats très répandus. |
| B3 | Bloquer un domaine bloque-t-il ses sous-domaines ? | **Non par défaut** (les listes publiques donnent des noms exacts). Les jokers (`*.exemple.com`) sont acceptés dans la liste perso des bloqués, comme pour les autorisés. |
| B4 | La liste perso gagne-t-elle toujours sur les sources ? | **Oui.** Un blocage perso bat une autorisation venant d'une source, et une autorisation perso bat une redirection venant d'une source. |
| B5 | Échec d'une partie des sources pendant une synchro | **On applique quand même**, en gardant les anciennes données des sources en échec, et on affiche lesquelles ont échoué. |
| B6 | Taille maximale d'une source | **50 Mo ou 2 millions d'entrées**, avec un message clair si c'est dépassé. |
| B7 | Sources en `http://` non chiffré | **Toujours refusées.** |
| B8 | Garder la lecture de date via les API GitHub, GitLab et Gist ? | **Non.** Les en-têtes standard (ETag, Last-Modified) suffisent, c'est plus simple et sans limite d'appels. |
| B9 | Suppressions (source, entrée perso) | **Possibilité d'annuler juste après sur mobile, confirmation sur TV.** |
| B10 | "Appliquer" alors que le blocage est coupé | **Le dire et proposer de l'activer.** |

### 6.3 Mode VPN

| N° | Question | Décision |
|---|---|---|
| C1 | DNS personnalisé et DNS chiffré (DoH) | **Pas dans la première version.** Le moteur est conçu pour, et la fonction est ajoutée dans une étape dédiée juste après avoir atteint les mêmes fonctions que l'ancienne app. |
| C2 | Secours quand le réseau ne donne aucun DNS | **Garder Cloudflare en secours** (indispensable), et le noter dans le journal de diagnostic. |
| C3 | "Activer au démarrage" alors que l'utilisateur avait coupé le VPN avant d'éteindre | **Respecter son choix** : le VPN ne revient que s'il était voulu. |
| C4 | DNS sur TCP | **Pas dans la première version** (rare, et complexe à faire dans un tunnel). |
| C5 | Réponse à un nom bloqué | **Garder la réponse actuelle** ("ce nom n'a pas d'adresse", délai de 5 s), qui marche bien. |
| C6 | Bouton de la notification | **"Désactiver"** au lieu de "Pause", puisque c'est un vrai arrêt. |
| C7 | Mode "bloquer les connexions sans VPN" d'Android | **Ne pas le proposer**, et avertir s'il est actif (les apps exclues perdent alors tout accès). |

### 6.4 Mode root

| N° | Question | Décision |
|---|---|---|
| D1 | Où écrire le fichier hosts ? | **Toujours `/system/etc/hosts`**, et l'app détecte elle-même un module "systemless hosts" (Magisk, KernelSU, APatch) pour guider l'utilisateur s'il manque. Le remontage de partition n'est tenté que s'il n'y a pas de module. |
| D2 | Garder le hosts d'origine pour le restaurer à l'identique ? | **Oui**, sauvegardé avant la première application. |
| D3 | Vérifier la copie du hosts | **Relecture réelle** du fichier installé et comparaison avec ce qui a été écrit. |
| D4 | Serveur web | **Certificat généré sur chaque appareil**, écoute en IPv4 et IPv6, relance automatique s'il meurt, raison affichée s'il ne démarre pas, arrêt quand on quitte le mode root. |
| D5 | "Ouvrir le fichier hosts" (cassé aujourd'hui) | **Le remplacer par un affichage en lecture seule dans l'app.** |
| D6 | Journal DNS root | **Capturer aussi les nouveaux types de requêtes d'Android récent**, limiter la taille, arrêter la capture quand on quitte le mode root. Utilisable aussi sur TV. |
| D7 | Réglage "Cacher le message de redémarrage" (sans effet) | **Le supprimer.** |

### 6.5 Fonctions annexes et interface

| N° | Question | Décision |
|---|---|---|
| E1 | Scanner d'adwares | **Retiré.** Il est déjà inaccessible dans l'app actuelle, sa liste date de plusieurs années, et il réclame une permission sensible. |
| E2 | Canal beta pour les mises à jour de l'app | **Pas pour l'instant.** Un seul canal stable. |
| E3 | Comparaison de versions | **Seuls les trois nombres x.y.z comptent**, tout suffixe (`-c`) est ignoré. |
| E4 | Format de sauvegarde | **Ajouter un numéro de version et les réglages**, en continuant à lire les anciens fichiers. Une restauration se fait en entier ou pas du tout. |
| E5 | Commandes externes (Tasker) | **Garder START et STOP à l'identique**, et ajouter TOGGLE. |
| E6 | Journal de diagnostic | **Accessible dans les deux modes**, exporté en fichier. |
| E7 | Étapes batterie de l'assistant | **Sautées en mode root**, et accessibles plus tard depuis les réglages. |
| E8 | Compteur "Bloqués" de l'accueil | **Afficher ce qui est réellement bloqué** (sans les noms autorisés ou redirigés). |
| E9 | Assistant : réactiver d'office toutes les sources après la première synchro | **Non.** |
| E10 | Assistant TV | **Aligné sur le mobile**, avec la demande de permission de notifications. |
| E11 | Thème sur TV | **Clair, sombre ou système**, sombre par défaut. |

## 7. Hors application (à reprendre en fin de chantier)

Ces éléments de `main` ne font pas partie de l'app. Ils seront refaits ou réadaptés à la fin, avant que cette branche puisse remplacer `main` :

- site web (`website/`) et sa publication ;
- fiches de store (`metadata/`) ;
- intégration continue (`.github/`), dont la vérification des traductions ;
- README, CHANGELOG, guide de publication ;
- licence : GPLv3 ou plus récente, comme l'app actuelle.

**À ne pas oublier côté publication :** la nouvelle version doit être signée avec la même clé que les versions 6.6.x, sinon personne ne pourra l'installer par-dessus.

## 8. Plan de réalisation

Chaque étape se termine par un commit sur `adaway-rust` et une liste de choses à tester sur téléphone et TV.

1. **Fondations**
   - projet Kotlin et Compose ;
   - moteur Rust compilé pour Android ;
   - nouveau stockage et reprise des anciennes données ;
   - thème, et navigation mobile et TV ;
   - accueil minimal et choix de la méthode.

   *Test principal : installer par-dessus la 6.6.1-c et retrouver ses sources, ses listes perso et ses réglages.*
2. **Sources et listes**
   - moteur de lecture et de calcul de la liste finale ;
   - synchronisation et mises à jour automatiques ;
   - écrans des sources, de l'édition et des listes (mobile et TV) ;
   - compteurs et bouton "Appliquer".
3. **Mode VPN complet**, avec toutes les situations du chapitre 02.
   *À tester au moins une semaine en usage réel.*
   *Point à vérifier : depuis Android 17, joindre une adresse du réseau local (le serveur DNS de la box, par exemple) peut demander la permission "accès au réseau local".*
4. **Mode root complet**, avec le serveur web et la capture DNS en Rust.
5. **Le reste des fonctions**
   - journal DNS ;
   - sauvegarde et restauration ;
   - mise à jour de l'app ;
   - tuile, commandes externes et notifications ;
   - aide et assistant complet.
6. **DNS personnalisé et DNS chiffré** (décision C1).
7. **Interface identique à celle de main**
   - chaque écran, mobile et TV, refait à l'identique de la version 6.6.1-c ;
   - les comportements décidés en section 6 (annulation, refus des doublons, erreurs par liste…) sont intégrés dans ce visuel ;
   - la séparation entre le moteur, la logique et l'interface rend ce travail possible sans toucher au reste.
8. **Finitions**
   - vérification de la version release ;
   - relecture complète ;
   - éléments de la section 7.

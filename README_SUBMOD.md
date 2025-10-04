# MySubMod - Système de Sous-modes pour Minecraft

Ce mod ajoute un système de sous-modes qui fonctionne côté client et serveur pour Minecraft Forge 1.20.1.

## Fonctionnalités

### Sous-modes disponibles
1. **Salle d'attente** - Mode par défaut, activé automatiquement au démarrage du serveur
2. **Sous-mode 1** - Jeu de survie de 15 minutes sur 4 îles avec système de bonbons et spawn points
3. **Sous-mode 2** - Mode personnalisé 2 (à implémenter)

### Interface Admin
- Accessible en appuyant sur la touche `M` en jeu
- Affiche le mode actuel
- Permet aux administrateurs de changer de sous-mode via des boutons
- Bouton 📊 pour gérer les logs (télécharger/supprimer)
- Bouton 📁 pour gérer les fichiers de spawn (upload/sélection/suppression)
- Les non-administrateurs peuvent voir l'interface mais ne peuvent pas changer de mode

## Installation

1. Placez le fichier JAR compilé dans le dossier `mods` de votre client et serveur Minecraft
2. Démarrez le serveur - la salle d'attente se lance automatiquement
3. Les joueurs peuvent se connecter et voir l'interface avec `M`
4. Les données de jeu sont automatiquement sauvegardées dans `mysubmod_data/`
5. Les fichiers de configuration de spawn sont dans `candy_spawn_configs/`

## Commandes Serveur

### Gestion des sous-modes
```
/submode set <mode>        - Change le sous-mode (waiting/attente, 1, 2)
/submode current           - Affiche le mode actuel
```

### Gestion des administrateurs
```
/submode admin add <joueur>     - Ajoute un administrateur
/submode admin remove <joueur>  - Supprime un administrateur
/submode admin list             - Liste les administrateurs
```

## Permissions

- Les administrateurs du serveur (niveau OP 2+) peuvent gérer les admins et changer les modes
- Les admins ajoutés via commande peuvent changer les sous-modes
- Tous les joueurs peuvent voir l'interface mais seuls les admins peuvent l'utiliser

## Utilisation

1. **Démarrage automatique** : La salle d'attente se lance automatiquement
2. **Changement de mode** :
   - Via l'interface (touche `M`) pour les admins
   - Via commandes serveur `/submode set <mode>`
3. **Synchronisation** : Tous les clients connectés reçoivent automatiquement le changement de mode

## Fonctionnalités Avancées

### Système de Spawn par Coordonnées
- **Coordonnées exactes** : Spawn précis au bloc spécifié (x,y,z)
- **Dispersion naturelle** : Bonbons dispersés dans un rayon de 3 blocs
- **Validation stricte** : X,Z sur les îles, Y entre 100-120
- **Fichiers de configuration** : Format `temps,quantité,x,y,z`
- **Sélection manuelle** : Touche N pour choisir le fichier avant la partie

### HUD en Temps Réel
- **Affichage non-invasif** : Coin supérieur droit de l'écran
- **Bonbons disponibles** : Compte par île avec couleurs distinctives
- **Mise à jour automatique** : Toutes les 2 secondes
- **Désactivation automatique** : À la fin de la partie

### Système de Logging
- **Enregistrement automatique** de toutes les sessions de jeu
- **Données détaillées** : positions, actions, événements, choix d'îles
- **Structure organisée** : dossiers par session avec horodatage
- **Fichiers séparés** : logs individuels par joueur + événements globaux

### Interface Utilisateur
- **Sélection d'îles** : Interface graphique pour choisir son île de départ (4 options)
- **Sélection de fichiers** : Upload et gestion des fichiers de configuration de spawn
- **Timer en jeu** : Affichage non-invasif du temps restant
- **Alertes automatiques** : Notifications aux moments clés
- **Restrictions visuelles** : Messages d'information pour les actions interdites

### Gestion des Fichiers de Spawn
- **Sélection (Touche N)** : Menu moderne pour choisir le fichier de configuration
- **Lancement automatique** : Sélectionner un fichier démarre la partie immédiatement
- **Protection** : Impossible de changer de fichier pendant une partie en cours
- **Upload (Bouton 📁)** : Interface pour téléverser des fichiers personnalisés
- **Validation stricte** : Vérification format 5 paramètres, coordonnées sur îles, Y entre 100-120
- **Suppression** : Gestion des fichiers via interface (default.txt protégé)
- **Actualisation** : Requête serveur automatique pour liste fraîche

### Gestion des Logs
- **Téléchargement** : Logs compressés en ZIP dans le dossier Downloads
- **Téléchargement en masse** : Option pour télécharger tous les logs en un fichier
- **Suppression sélective** : Interface pour supprimer des sessions individuelles
- **Suppression en masse** : Option pour nettoyer tous les logs
- **Liste défilante** : Interface moderne avec sélection par clic

## Architecture Technique

### Communication Réseau
- **Client-serveur** : Communication via packets réseau
- **Validation** : Vérification côté serveur des permissions admin
- **Synchronisation** : Mise à jour en temps réel des compteurs de bonbons

### Interface Graphique
- **GUI Minecraft** : Boutons pour chaque sous-mode
- **HUD Overlay** : Affichage des ressources disponibles
- **Écrans personnalisés** : Sélection d'îles et de fichiers

### Système de Jeu
- **Génération procédurale** : 4 îles carrées autour d'un carré central (20x20)
- **Distances** : 360 blocs entre le centre et chaque île
- **Spawn points** : Système aléatoire avec contraintes de distance
- **Téléportation sécurisée** : Chargement de chunks avant téléportation

### Gestion des Données
- **Logging complet** : Analyse comportementale détaillée
- **Tracking des ressources** : Comptage en temps réel par île
- **Configuration flexible** : Fichiers de spawn personnalisables

### Effets et Restrictions
- **Bonbons lumineux** : Effet de glowing pour visibilité
- **Barrières invisibles** : Protection des îles et chemins
- **Restrictions joueurs** : Limitations des actions en jeu
- **Fin de partie** : Automatique si tous morts ou timer expiré

## Nouveautés de la Dernière Version

### Optimisations et Correctifs (4 octobre 2025)
- **Nettoyage des logs** : Réduction de 26% du volume (116 → 86 log statements)
- **Code épuré** : Suppression de 5 méthodes/champs inutilisés
- **Cooldown de sous-modes** : Protection 5 secondes contre les changements trop rapides
- **Logging rétroactif** : File d'attente pour événements avant création du dataLogger
- **Détection carrée** : Spawn de monstres bloqués avec détection carrée (plus cercle)
- **Jour permanent** : Cycle jour/nuit bloqué pendant TOUT le sous-mode (pas seulement le jeu)

### Améliorations Majeures (3 octobre 2025)
- **Système de spawn refait** : Coordonnées exactes (x,y,z) au lieu de spawn points aléatoires
- **Sélection manuelle** : Touche N pour choisir le fichier, lancement immédiat de la partie
- **Validation carrée** : Vérification des coordonnées dans les limites carrées des îles
- **Max bonbons augmenté** : 100 bonbons max par spawn (au lieu de 50)
- **Code nettoyé** : SpawnPointManager supprimé, code redondant éliminé
- **Documentation complète** : CANDY_SPAWN_GUIDE.md avec exemples et carte des îles

### Améliorations Précédentes (2 octobre 2025)
- **Hologrammes** : Indicateurs directionnels au-dessus des tours de laine (texte espacé)
- **Protection améliorée** : Blocage de tous les items au sol (sauf bonbons avec glowingTag)
- **Sprint désactivé** : Vitesse de sprint = vitesse marche via attribut modifier
- **Gestion des logs** : Interface 📊 complète (téléchargement ZIP, suppression)
- **Interface modernisée** : Listes défilantes avec sélection par clic
- **Correction HUD** : Le HUD ne persiste plus après déconnexion/reconnexion
- **Déconnexion/Reconnexion** : Système complet avec pénalités, téléportation aléatoire, inventaire préservé

### Fonctionnalités du Sous-mode 1
- **4 îles carrées** de tailles différentes (60×60, 90×90, 120×120, 150×150)
- **Carré central** de spawn (20×20) avec tours de laine colorées + hologrammes directionnels
- **Système de spawn par coordonnées** : Fichiers avec format `temps,quantité,x,y,z`
- **Sélection de fichier (Touche N)** : Choisir le fichier de configuration avant chaque partie
- **HUD des ressources** : Nombre de bonbons disponibles par île en temps réel (coin supérieur droit)
- **Logging complet** : Choix d'îles, positions toutes les 5s, déconnexions, tout avec timestamps
- **Déconnexion/Reconnexion** : Pénalité -4 cœurs, téléportation île aléatoire, inventaire préservé
- **Téléportation sécurisée** avec chargement de chunks (évite déconnexions)
- **Fin automatique** si tous morts ou timer expiré (double condition)
- **Protection complète** : Aucun bloc cassé/placé, aucun craft, sprint désactivé
- **Protection environnement** : Items bloqués (sauf bonbons), barrières invisibles, pissenlits bloqués
- **Monstres bloqués** : Détection carrée précise correspondant aux îles (SMALL: 35, MEDIUM: 50, LARGE: 65, EXTRA_LARGE: 80)
- **Jour permanent** : Cycle jour/nuit bloqué pendant toute la durée du sous-mode
- **Monde vide** : Serveur configuré avec monde void par défaut
- **Visibilité augmentée** : Distance de rendu des entités 150% (server.properties)

Le mod garantit que seuls les administrateurs autorisés peuvent effectuer des changements de sous-modes, tout en permettant à tous les joueurs de voir l'état actuel du système et de participer pleinement aux parties.

## Documentation Complète

- **SUBMODE1_GUIDE.md** : Guide exhaustif du Sous-mode 1 avec toutes les fonctionnalités
- **CANDY_SPAWN_GUIDE.md** : Guide d'utilisation des fichiers de spawn de bonbons (format, exemples, limites)
- **CHANGELOG.md** : Historique complet de toutes les modifications
- **README_SUBMOD.md** : Ce fichier - Vue d'ensemble du système

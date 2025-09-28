# Guide du Sous-mode 1 - Survie sur Îles

## Vue d'ensemble

Le sous-mode 1 est un mode de survie de 15 minutes où les joueurs doivent survivre sur des îles en collectant et consommant des bonbons pour maintenir leur santé.

## Fonctionnalités principales

### 🏝️ **Système d'îles carrées**
- **3 îles carrées générées automatiquement** :
  - **Petite île** (15x15 blocs) : Compacte, ressources limitées
  - **Île moyenne** (25x25 blocs) : Équilibrée
  - **Grande île** (35x35 blocs) : Plus d'espace, plus de ressources
- **Barrières invisibles** : Empêchent la chute dans l'eau avec ouvertures pour les chemins
- **Chemins de pierre** : Relient les îles entre elles
- **Décoration naturelle** : Arbres, fleurs, herbe haute
- **Génération procédurale** avec variations de terrain et bordures naturelles

### 🎯 **Phase de sélection (30 secondes)**
- **Interface automatique** s'ouvre pour tous les joueurs non-admin
- **Choix d'île** via boutons dans l'interface
- **Attribution automatique** si pas de sélection dans les 30 secondes
- **Téléportation simultanée** de tous les joueurs vers leurs îles

### ⏱️ **Timer de jeu (15 minutes)**
- **Affichage non-invasif** en haut à droite de l'écran
- **Alertes temporelles** :
  - 5 minutes restantes
  - 2 minutes restantes
  - 1 minute restante
  - Compte à rebours final (30s, 10s, 9s...1s)
- **Couleurs d'alerte** : Jaune → Orange → Rouge

### 💔 **Système de santé**
- **Santé initiale** : 100% santé, 50% faim au démarrage
- **Dégradation automatique** : -0.5 cœur toutes les 10 secondes
- **Alerte santé critique** quand ≤ 1 cœur restant
- **Mort automatique** à 0 cœur → téléportation vers zone spectateur

### 🍬 **Système de bonbons**
- **Seul moyen de récupérer de la santé** (+2 cœurs par bonbon)
- **Distribution** : 35 bonbons par joueur (50% grande île, 30% moyenne, 20% petite)
- **Spawn étalé** : Répartis sur les 15 minutes de jeu
- **Expiration** : 2 minutes après apparition
- **Seul objet autorisé** dans l'inventaire
- **Visibilité améliorée** : Effet lumineux et flottement pour les voir de loin

#### Propriétés des bonbons :
- **Récupération** : +2 cœurs de santé
- **Partageables** : Peuvent être jetés et ramassés par d'autres
- **Destructibles** : Peuvent être détruits
- **Effet lumineux** : Brillent avec un contour visible à travers les blocs
- **Flottement** : Restent en l'air pour une meilleure visibilité

### 👥 **Gestion des joueurs**

#### **Joueurs vivants**
- Téléportés sur l'île choisie
- Commencent avec 100% santé et 50% faim
- Subissent la dégradation de santé
- Peuvent collecter et consommer des bonbons
- Peuvent se déplacer entre les îles via les chemins
- Suivis par le système de logging

#### **Joueurs morts**
- Téléportés vers la plateforme spectateur
- Santé restaurée à 100%
- Soumis aux mêmes restrictions que la salle d'attente
- Annonce publique de mort

#### **Administrateurs**
- Téléportés automatiquement vers la plateforme spectateur
- Peuvent observer le jeu
- Gardent leurs privilèges pour changer de mode

### 🚫 **Restrictions strictes**
Les joueurs vivants **NE PEUVENT PAS** :
- ❌ Attaquer d'autres joueurs ou entités
- ❌ Interagir avec des blocs (clic droit)
- ❌ Casser des blocs
- ❌ Placer des blocs
- ❌ Fabriquer des objets
- ❌ Ramasser d'autres objets que les bonbons

### 📊 **Système de logging complet**
Toutes les actions sont enregistrées dans `mysubmod_data/submode1_game_[timestamp]/` :

#### **Logs par joueur** (`[nom_joueur]_log.txt`) :
- **Positions** : Enregistrées toutes les 5 secondes
- **Consommation de bonbons** : Moment, position et santé après consommation
- **Ramassage de bonbons** : Position du bonbon et du joueur
- **Changements de santé** : Ancienne → nouvelle valeur avec position
- **Mort** : Position et moment
- **Sélection d'île** : Île choisie au début

#### **Logs globaux** (`game_events.txt`) :
- Début/fin de partie
- Spawn de bonbons (position et île)
- Événements système

### 🎉 **Fin de partie**
- **Message de félicitations** affiché à tous
- **Retour automatique** vers la salle d'attente après 5 secondes
- **Sauvegarde** de toutes les données de la session
- **Nettoyage complet** de la carte

## Commandes administratives

```bash
# Lancer le sous-mode 1
/submode set 1

# Vérifier le mode actuel
/submode current

# Retourner à la salle d'attente
/submode set waiting
```

## Architecture technique

### **Classes principales**
- `SubMode1Manager` : Gestion principale du mode
- `IslandGenerator` : Génération procédurale des îles
- `SubMode1HealthManager` : Système de dégradation de santé
- `SubMode1CandyManager` : Gestion des bonbons (spawn/expiration)
- `SubMode1DataLogger` : Système de logging complet
- `GameTimer` : Gestion du timer de 15 minutes

### **Interface utilisateur**
- `IslandSelectionScreen` : Interface de sélection d'île
- `SubMode1HUD` : Affichage du timer en jeu
- `ClientGameTimer` : Gestion côté client du timer

### **Réseau**
- `IslandSelectionPacket` : Ouverture de l'interface de sélection
- `IslandChoicePacket` : Envoi du choix d'île au serveur
- `GameTimerPacket` : Synchronisation du timer

## Données collectées

Le système collecte des données précieuses pour l'analyse comportementale :
- **Patterns de mouvement** des joueurs
- **Stratégies de collecte** de bonbons
- **Gestion des ressources** (timing de consommation)
- **Zones de survie préférées** sur chaque île
- **Durée de survie** par joueur

Cette implémentation complète offre une expérience de jeu équilibrée, competitive et entièrement trackée pour l'analyse de données.
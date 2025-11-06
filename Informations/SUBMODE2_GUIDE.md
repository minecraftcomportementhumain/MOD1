# Guide du Sous-Mode 2 - Système de Spécialisation

## 📋 Vue d'ensemble

Le Sous-Mode 2 est un mode de jeu compétitif basé sur un système de **spécialisation en ressources**. Les joueurs sont assignés aléatoirement à un type de ressource (Type A ou Type B) et doivent gérer stratégiquement leur consommation de bonbons.

### Différences avec le Sous-Mode 1

| Aspect | Sous-Mode 1 | Sous-Mode 2 |
|--------|-------------|-------------|
| **Bonbons** | Un seul type (bleu) | Deux types (bleu et rouge) |
| **Spécialisation** | Aucune | Dynamique (change selon collecte) |
| **Pénalités** | Aucune | 2min 45s pour changement |
| **Stratégie** | Collecte simple | Gestion des ressources et spécialisation |

---

## 🎮 Fonctionnalités principales

### 1. Système de spécialisation

**Spécialisation dynamique** :
- La spécialisation se définit **automatiquement lors de la première collecte** de bonbon
- Premier bonbon bleu collecté → Spécialisation BLUE (bonbons bleus)
- Premier bonbon rouge collecté → Spécialisation RED (bonbons rouges)
- La spécialisation peut **changer** pendant la partie

**Effets de la spécialisation** :
- Bonbon de votre type : **+1 cœur** (100% efficacité)
- Bonbon de l'autre type : **+0.75 cœur** (75% efficacité) + **2 minutes 45 secondes de pénalité**

### 2. Système de pénalités

**Déclenchement** :
- Collecter un bonbon du type opposé à votre spécialisation actuelle
- **Change automatiquement votre spécialisation** vers le nouveau type
- Durée de la pénalité : **2 minutes 45 secondes** (165 secondes)

**Effets pendant la pénalité** :
- Healing réduit à 75% (0.75 cœur au lieu de 1 cœur)
- Timer visible au centre-haut de l'écran : "⚠ PÉNALITÉ: MM:SS"
- Couleur rouge pour alerter le joueur
- La pénalité reste active même si vous collectez d'autres bonbons de votre nouvelle spécialisation

**Stratégie** :
- Minimiser les changements de spécialisation
- Planifier les changements stratégiquement
- Accepter la pénalité seulement si nécessaire pour la survie

### 3. Types de bonbons

#### Bonbon Bleu (BLUE)
- **Item** : `candy_blue`
- **Texture** : Bonbon bleu distinct avec effet brillant
- **Efficacité** :
  - Spécialisation BLUE : +1 cœur (100%)
  - Spécialisation RED : +0.75 cœur (75%) + pénalité 2min 45s

#### Bonbon Rouge (RED)
- **Item** : `candy_red`
- **Texture** : Bonbon rouge distinct avec effet brillant
- **Efficacité** :
  - Spécialisation RED : +1 cœur (100%)
  - Spécialisation BLUE : +0.75 cœur (75%) + pénalité 2min 45s

### 4. Distribution des bonbons

**Spawn aléatoire** :
- 50% de chance pour chaque type à chaque spawn
- Distribution équilibrée sur l'ensemble de la partie
- Même système de fichiers de spawn que SubMode1

**Fichier de configuration** :
- Format : `temps,quantité,x,y,z`
- Exemple : `10,5,0,110,-350` spawne 5 bonbons à 10 secondes
- Type déterminé aléatoirement lors du spawn

---

## 🗺️ Structure du terrain

Identique au Sous-Mode 1 :

### Carré central
- **Dimensions** : 20x20 blocs
- **Position** : (0, 0) coordonnées X/Z
- **Fonction** : Point de spawn initial et phase de sélection

### 4 Îles

| Île | Dimensions | Distance | Couleur |
|-----|-----------|----------|---------|
| Petite | 60x60 | 360 blocs | Blanc |
| Moyenne | 90x90 | 360 blocs | Vert |
| Grande | 120x120 | 360 blocs | Bleu |
| Très Grande | 150x150 | 360 blocs | Orange |

### Chemins
- **Largeur** : 11 blocs
- **Longueur** : 360 blocs
- **Matériau** : Pierre
- **Connexions** : Relient chaque île au carré central

---

## 🎯 Déroulement d'une partie

### Phase 1 : Sélection du fichier (Admin uniquement)

**Touche N** : Ouvre le menu de sélection
- Liste des fichiers de spawn disponibles
- Upload de nouveaux fichiers
- Suppression de fichiers (sauf default.txt)
- Confirmation lance la partie

### Phase 2 : Sélection de l'île (30 secondes)

**Choix de l'île** :
- Interface avec 4 options d'îles
- Sélection automatique si pas de choix après 30s
- Téléportation au centre de l'île choisie

**Spécialisation** :
- Aucune spécialisation au départ
- La spécialisation se définit automatiquement lors de la **première collecte** de bonbon
- Information visible dans les logs et messages système

### Phase 3 : Partie active (15 minutes)

**Objectif** :
- Survivre le plus longtemps possible
- Collecter des bonbons pour maintenir sa santé
- Gérer stratégiquement sa spécialisation

**Mécaniques** :
- Santé initiale : 10 cœurs (20 points de santé)
- Faim initiale : 5 barres (50%)
- Dégradation : -0.5 cœur (1 point) toutes les 10 secondes
- Sprint désactivé (vitesse de marche uniquement)
- Spawn de bonbons selon le fichier sélectionné

**HUD affiché** :
1. **Timer** (haut-gauche) : Temps restant en MM:SS
2. **Compteur bonbons** (haut-droite) : Nombre par île ET par type (bleu/rouge) avec couleurs
3. **Timer pénalité** (centre-haut) : Si pénalité active (MM:SS restantes)

### Phase 4 : Fin de partie

**Conditions de fin** :
- Timer de 15 minutes écoulé, OU
- Tous les joueurs sont morts

**Actions automatiques** :
- Affichage du résultat
- Sauvegarde des logs
- Nettoyage du terrain
- Retour à la Waiting Room

---

## 📊 Logging des données

### Structure des fichiers

```
mysubmod_data/
└── submode2_game_[timestamp]/
    ├── game_events.txt          # Événements globaux
    ├── [joueur1]_log.txt        # Logs individuels
    ├── [joueur2]_log.txt
    └── ...
```

### Données enregistrées

**Événements globaux** :
- Activation/désactivation du mode
- Sélection du fichier de spawn
- Début/fin de partie
- Spawn de bonbons (x, y, z, type, timestamp)

**Logs individuels par joueur** :
- Sélection d'île (manuel/automatique)
- Spécialisation définie/changée (BLUE/RED, timestamp)
- Ramassage de bonbons (position, type, timestamp)
- Consommation de bonbons (type, efficacité appliquée, timestamp)
- Pénalités appliquées (début, durée restante, timestamp)
- Changements de santé (avant, après, multiplicateur, timestamp)
- Mort (position, timestamp)
- Déconnexions/reconnexions

---

## 🎛️ Interface administrateur

### Menu de contrôle (Touche M)

**Bouton "Sous-mode 2"** :
- Active le Sous-Mode 2
- Désactive les autres modes
- Lance la phase de sélection de fichier

**Changement de mode** :
- Cooldown de 5 secondes entre changements
- Nettoyage automatique du mode précédent

### Menu de sélection de fichiers (Touche N)

**Fonctionnalités** :
- Visualiser les fichiers disponibles
- Sélectionner un fichier (lance immédiatement la partie)
- Upload de nouveaux fichiers
- Suppression de fichiers
- Actualiser la liste

**Protection** :
- Impossible d'ouvrir pendant une partie active
- `default.txt` ne peut pas être supprimé

---

## 🔧 Configuration des fichiers de spawn

### Format du fichier

**DIFFÉRENT du Sous-Mode 1** - inclut le type de bonbon :
```
temps,quantité,x,y,z,type
```

**Paramètres** :
- `temps` : Secondes depuis le début (0-900)
- `quantité` : Nombre de bonbons (1-100)
- `x, y, z` : Coordonnées exactes du spawn
  - Y : 100-120 strictement
  - X/Z : Dans les limites d'une île
- `type` : **A** (Bonbon Bleu) ou **B** (Bonbon Rouge)

**Exemple** :
```
60,5,0,101,-360,A
120,3,360,101,0,B
180,2,0,101,360,A
240,4,-360,101,0,B
```

### Contrôle du type de bonbon

Le type de bonbon est **défini explicitement dans le fichier** :
- Type **A** = Bonbon Bleu (BLUE)
- Type **B** = Bonbon Rouge (RED)
- Vous pouvez planifier stratégiquement la distribution des deux types
- Permet de créer des patterns de spawn personnalisés

---

## 🎨 Interface utilisateur

### HUD Timer de jeu
- **Position** : Coin supérieur gauche
- **Format** : "Temps: MM:SS"
- **Couleurs** :
  - Vert : > 10 minutes restantes
  - Jaune : 5-10 minutes
  - Rouge : < 5 minutes

### HUD Compteur de bonbons
- **Position** : Coin supérieur droit
- **Format** : Par île avec couleurs
  - "Petite: X" (blanc)
  - "Moyenne: X" (vert)
  - "Grande: X" (bleu)
  - "Très Grande: X" (orange)
- **Mise à jour** : Toutes les 2 secondes

### HUD Timer de pénalité
- **Position** : Centre-haut de l'écran
- **Format** : "⚠ PÉNALITÉ: MM:SS"
- **Couleur** : Rouge vif avec clignotement
- **Affichage** : Uniquement pendant les 2 minutes 45 secondes (165s) de pénalité
- **Information** : Indique le temps restant avant la fin de la pénalité

---

## 🛡️ Protections et restrictions

### Interactions bloquées
- ❌ Casser des blocs
- ❌ Placer des blocs
- ❌ Interagir avec les blocs (coffres, portes, etc.)
- ❌ Dropper des items (sauf mort)
- ❌ Sprint
- ✅ Consommer des bonbons (autorisé)

### Protection de l'environnement
- Blocs protégés contre modifications
- Items bloqués (sauf bonbons du système)
- Monstres hostiles ne peuvent pas spawn
- Cycle jour/nuit bloqué à midi

### Mode spectateur
- Joueurs morts deviennent spectateurs
- HUDs cachés en mode spectateur
- Peuvent observer mais pas interagir

---

## 📈 Stratégies recommandées

### Gestion de la spécialisation

**Définition initiale** :
- Première collecte de bonbon définit votre spécialisation
- Message système confirme votre spécialisation (BLUE ou RED)
- Pas de pénalité à la première collecte

**Changement de spécialisation** :
- Collecter un bonbon du type opposé change votre spécialisation
- Déclenche automatiquement une pénalité de 2min 45s
- Vous restaurez 0.75 cœur au lieu de 1 cœur pendant la pénalité
- La pénalité reste active même après plusieurs consommations

**Optimisation** :
- Éviter de changer de spécialisation sauf nécessité absolue
- Planifier les changements pendant les moments de santé élevée
- Mémoriser les patterns de spawn pour votre type actuel

### Gestion des îles

**Choix de l'île** :
- Grande île = Plus d'espace mais plus de distance
- Petite île = Moins d'espace mais spawns concentrés
- Considérer la distribution prévue dans le fichier

**Mobilité** :
- Utiliser les chemins pour traverser rapidement
- Surveiller les spawns sur les autres îles
- Anticiper les déplacements des autres joueurs

### Timing optimal

**Début de partie** :
- Choisir stratégiquement votre première collecte (définit votre spécialisation)
- Établir un circuit de collecte efficace
- Mémoriser les emplacements de spawn

**Mi-partie** :
- Rester fidèle à votre spécialisation autant que possible
- Éviter les changements sauf urgence absolue
- Observer les patterns de spawn des deux types

**Fin de partie** :
- Devenir plus flexible avec les changements de spécialisation
- Accepter les pénalités si nécessaire pour survivre
- La pénalité de 2min 45s devient moins critique en fin de partie

---

## 🔍 Analyse des logs

Les logs peuvent être utilisés pour :
- Analyser les patterns de comportement
- Comparer les stratégies entre joueurs
- Étudier l'impact des spécialisations
- Évaluer l'équilibre du système de pénalités

**Téléchargement** :
- Menu de gestion des logs (touche M → Logs)
- Téléchargement sélectif ou en masse
- Format ZIP dans le dossier Downloads

---

---

## 📝 Notes de version

**Dernière mise à jour : 6 novembre 2025**

### Spécifications techniques
- Pénalité de changement : 2 minutes 45 secondes (165 secondes)
- Efficacité réduite pendant pénalité : 75% (0.75 cœur)
- Système de spécialisation : Dynamique (défini à la première collecte)
- Dégradation de santé : -0.5 cœur toutes les 10 secondes
- Durée totale de partie : 15 minutes (900 secondes)

*Guide créé le 30 octobre 2025 - Mis à jour le 6 novembre 2025*

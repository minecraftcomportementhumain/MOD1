# Guide du Sous-Mode 2 - Système de Spécialisation

## 📋 Vue d'ensemble

Le Sous-Mode 2 est un mode de jeu compétitif basé sur un système de **spécialisation en ressources**. Les joueurs sont assignés aléatoirement à un type de ressource (Type A ou Type B) et doivent gérer stratégiquement leur consommation de bonbons.

### Différences avec le Sous-Mode 1

| Aspect | Sous-Mode 1 | Sous-Mode 2 |
|--------|-------------|-------------|
| **Bonbons** | Un seul type (bleu) | Deux types (bleu et rouge) |
| **Spécialisation** | Aucune | Type A ou Type B |
| **Pénalités** | Aucune | 30s pour mauvais type |
| **Stratégie** | Collecte simple | Gestion des ressources |

---

## 🎮 Fonctionnalités principales

### 1. Système de spécialisation

**Assignation automatique** :
- Chaque joueur reçoit aléatoirement une spécialisation lors de la sélection d'île
- **Type A** : Efficacité maximale avec bonbons bleus
- **Type B** : Efficacité maximale avec bonbons rouges

**Effets de la spécialisation** :
- Bonbon de votre type : **+1 cœur** (100% efficacité)
- Bonbon de l'autre type : **+0.5 cœur** + **30 secondes de pénalité**

### 2. Système de pénalités

**Déclenchement** :
- Consommer un bonbon du type opposé à votre spécialisation
- Durée : 30 secondes

**Effets pendant la pénalité** :
- Healing réduit de moitié (0.5 cœur au lieu de 1)
- Timer visible au centre-haut de l'écran : "⚠ PÉNALITÉ: XXs"
- Couleur rouge pour alerter le joueur

**Stratégie** :
- Privilégier les bonbons de votre type
- Utiliser les bonbons opposés uniquement en urgence
- Planifier les déplacements selon les spawns de bonbons

### 3. Types de bonbons

#### Bonbon Bleu (Type A)
- **Item** : `candy_blue`
- **Texture** : Bonbon bleu distinct
- **Efficacité** :
  - Joueur Type A : +1 cœur
  - Joueur Type B : +0.5 cœur + pénalité 30s

#### Bonbon Rouge (Type B)
- **Item** : `candy_red`
- **Texture** : Bonbon rouge distinct
- **Efficacité** :
  - Joueur Type B : +1 cœur
  - Joueur Type A : +0.5 cœur + pénalité 30s

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
- Assignation **aléatoire de la spécialisation** à ce moment
- Sélection automatique si pas de choix après 30s
- Téléportation au centre de l'île choisie

**Assignation de spécialisation** :
- Type A ou Type B assigné aléatoirement
- Information visible dans les logs
- Pas d'indication visuelle pendant la partie (stratégie)

### Phase 3 : Partie active (15 minutes)

**Objectif** :
- Survivre le plus longtemps possible
- Collecter des bonbons pour maintenir sa santé
- Gérer stratégiquement sa spécialisation

**Mécaniques** :
- Santé initiale : 10 cœurs (100%)
- Faim initiale : 5 barres (50%)
- Dégradation : -0.5 cœur toutes les 10 secondes
- Sprint désactivé
- Spawn de bonbons selon le fichier sélectionné

**HUD affiché** :
1. **Timer** (haut-gauche) : Temps restant en MM:SS
2. **Compteur bonbons** (haut-droite) : Nombre par île avec couleurs
3. **Timer pénalité** (centre-haut) : Si pénalité active

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
- Spécialisation assignée (TYPE_A/TYPE_B)
- Ramassage de bonbons (position, type, timestamp)
- Consommation de bonbons (type, compatibilité, timestamp)
- Pénalités appliquées (début, fin, timestamp)
- Changements de santé (avant, après, timestamp)
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

Identique au Sous-Mode 1 :
```
temps,quantité,x,y,z
```

**Paramètres** :
- `temps` : Secondes depuis le début (0-900)
- `quantité` : Nombre de bonbons (1-100)
- `x, y, z` : Coordonnées exactes du spawn
  - Y : 100-120 strictement
  - X/Z : Dans les limites d'une île

**Exemple** :
```
10,5,0,110,-350
30,3,370,105,0
60,8,-400,110,-30
```

### Type de bonbon

Le type (bleu ou rouge) est déterminé **aléatoirement** à chaque spawn :
- 50% de chance pour bonbon bleu
- 50% de chance pour bonbon rouge
- Distribution équilibrée sur l'ensemble de la partie

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
- **Format** : "⚠ PÉNALITÉ: XXs"
- **Couleur** : Rouge vif
- **Affichage** : Uniquement pendant les 30 secondes de pénalité

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

**Découverte de votre type** :
- Première consommation révèle votre spécialisation
- Si pénalité → Vous avez consommé le mauvais type
- Si guérison complète → Vous avez consommé le bon type

**Optimisation** :
- Mémoriser les positions de spawn de votre type
- Planifier les déplacements selon les spawns
- Garder des bonbons de l'autre type pour urgences

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
- Collecter rapidement les premiers bonbons
- Identifier votre spécialisation tôt
- Établir un circuit de collecte

**Mi-partie** :
- Maintenir un stock de bonbons de votre type
- Éviter les pénalités autant que possible
- Observer les patterns de spawn

**Fin de partie** :
- Devenir plus agressif dans la collecte
- Accepter les pénalités si nécessaire pour survivre
- Optimiser chaque déplacement

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

*Guide créé le 30 octobre 2025*

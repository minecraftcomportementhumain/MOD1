# 🚀 Configuration GitHub pour Auto-Distribution du Mod

Guide rapide pour configurer GitHub et activer la distribution automatique.

---

## 📋 Étapes de Configuration

### 1️⃣ Créer le Repository GitHub (pour le client)

1. **Créer un nouveau compte GitHub** (si le client n'en a pas):
   - Allez sur https://github.com/signup
   - Créez un compte (ex: `UniversiteLab`)

2. **Créer un nouveau repository:**
   - Cliquez sur **"New repository"**
   - **Nom:** Par exemple `minecraft-mvt-experiment`
   - **Visibilité:** ⚠️ **PUBLIC** (important pour les téléchargements)
   - ✅ Cochez **"Add a README file"**
   - Cliquez **"Create repository"**

### 2️⃣ Pousser le Code sur GitHub

Dans votre terminal/PowerShell:

```bash
cd "C:\Users\Olivier Lafontaine\Desktop\Projet Minecraft\forge-1.20.1-47.3.39-mdk"

# Initialiser Git (si pas déjà fait)
git init

# Ajouter le remote (REMPLACEZ avec les vraies valeurs!)
git remote add origin https://github.com/USERNAME/REPO_NAME.git

# Ou si déjà configuré, changer le remote:
git remote set-url origin https://github.com/USERNAME/REPO_NAME.git

# Ajouter tous les fichiers
git add .

# Commit
git commit -m "Setup auto-distribution avec GitHub Actions"

# Pousser sur GitHub
git push -u origin main
```

### 3️⃣ Activer GitHub Actions

La GitHub Action se déclenchera **automatiquement** au premier push!

Pour vérifier:
1. Allez sur votre repository GitHub
2. Cliquez sur l'onglet **"Actions"**
3. Vous devriez voir un workflow en cours: **"Build and Release Mod"**
4. Attendez qu'il devienne vert ✅ (environ 2-3 minutes)

### 4️⃣ Vérifier la Release

1. Allez sur l'onglet **"Releases"** (à droite sur la page principale)
2. Vous devriez voir une release nommée **"Latest Build - [date]"**
3. Le fichier `.jar` du mod est téléchargeable! 🎉

---

## 🔄 Workflow Automatique

### Comment ça marche?

**À chaque `git push` sur `main`:**

1. ✅ GitHub Action se déclenche automatiquement
2. ✅ Build le mod avec Gradle
3. ✅ Supprime l'ancienne release `latest`
4. ✅ Crée une nouvelle release `latest` avec le `.jar`
5. ✅ Les clients peuvent télécharger immédiatement

### Flux de développement:

```bash
# 1. Modifier le code
vim src/main/java/...

# 2. Tester localement
./gradlew build

# 3. Commit et push
git add .
git commit -m "Fix: Protection DoS avec éviction basée sur l'âge"
git push

# 4. Automatiquement:
# - Build sur GitHub ✅
# - Nouvelle release créée ✅
# - Clients peuvent télécharger ✅
```

---

## 📦 Distribution aux Clients

### Option A: Script de mise à jour (Recommandé)

1. **Donnez aux clients:**
   - `update-mod.bat` (Windows)
   - `update-mod.sh` (Linux/Mac)
   - `INSTALLATION_CLIENT.md` (instructions)

2. **Les clients configurent le script** (une seule fois):
   - Modifier `GITHUB_USER` et `GITHUB_REPO`
   - Modifier `PRISM_INSTANCE_NAME`

3. **Les clients exécutent le script** (à chaque mise à jour):
   - Double-clic sur `update-mod.bat`
   - Le mod se télécharge automatiquement!

### Option B: Téléchargement manuel

Les clients vont sur:
```
https://github.com/USERNAME/REPO_NAME/releases/latest
```

Et téléchargent le fichier `.jar`.

---

## 🔧 Personnalisation

### Modifier le nom du fichier .jar

Dans `build.gradle`, ligne ~15:
```gradle
archiveBaseName = 'mysubmod'
version = '1.0-SNAPSHOT'
```

### Déclencher manuellement un build

1. Allez sur GitHub → **Actions**
2. Sélectionnez **"Build and Release Mod"**
3. Cliquez **"Run workflow"** → **"Run workflow"**

### Voir les logs de build

1. GitHub → **Actions**
2. Cliquez sur le workflow en cours
3. Consultez les logs pour déboguer

---

## ⚙️ Configuration Optionnelle

### Permissions GitHub Actions

Si vous obtenez une erreur de permissions:

1. GitHub → **Settings** (du repository)
2. **Actions** → **General**
3. **Workflow permissions** → Sélectionnez **"Read and write permissions"**
4. ✅ Cochez **"Allow GitHub Actions to create and approve pull requests"**
5. **Save**

### Branches protégées

Pour éviter les pushes accidentels sur `main`:

1. **Settings** → **Branches**
2. **Add branch protection rule**
3. **Branch name pattern:** `main`
4. ✅ Require pull request reviews before merging

---

## 📊 URLs Importantes

Remplacez `USERNAME` et `REPO_NAME`:

- **Repository:** `https://github.com/USERNAME/REPO_NAME`
- **Releases:** `https://github.com/USERNAME/REPO_NAME/releases`
- **Latest release:** `https://github.com/USERNAME/REPO_NAME/releases/latest`
- **Direct download:** `https://github.com/USERNAME/REPO_NAME/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar`
- **Actions:** `https://github.com/USERNAME/REPO_NAME/actions`

---

## ✅ Checklist Finale

Avant de distribuer aux clients:

- [ ] Repository GitHub créé et **PUBLIC**
- [ ] Code poussé sur `main`
- [ ] GitHub Action exécutée avec succès ✅
- [ ] Release `latest` créée avec le fichier `.jar`
- [ ] Scripts `update-mod.bat` et `update-mod.sh` configurés avec les bonnes valeurs
- [ ] Documentation `INSTALLATION_CLIENT.md` à jour
- [ ] Testé le téléchargement avec le script sur au moins 1 client

---

**Prêt!** Les clients peuvent maintenant télécharger et se mettre à jour automatiquement! 🎉

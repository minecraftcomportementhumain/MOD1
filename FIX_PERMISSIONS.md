# 🔧 Fix: Permissions GitHub Actions

Le build échoue avec l'erreur: **"Resource not accessible by integration"**

Cela signifie que GitHub Actions n'a pas la permission de créer des releases.

---

## ✅ Solution: Activer les Permissions

### Étape 1: Allez dans les Settings du Repository

1. Ouvrez: https://github.com/minecraftcomportementhumain/MOD1
2. Cliquez sur **Settings** (onglet en haut)

### Étape 2: Configurer les Permissions Actions

1. Dans le menu de gauche, cliquez sur **Actions** → **General**

2. Scrollez jusqu'à **"Workflow permissions"**

3. Sélectionnez: **"Read and write permissions"** ✅
   (au lieu de "Read repository contents and packages permissions")

4. ✅ Cochez: **"Allow GitHub Actions to create and approve pull requests"**

5. Cliquez sur **Save**

---

## 🔄 Re-déclencher le Build

Une fois les permissions configurées:

### Option A: Re-run depuis GitHub (Plus Simple)

1. Allez sur: https://github.com/minecraftcomportementhumain/MOD1/actions
2. Cliquez sur le workflow qui a échoué
3. Cliquez sur **"Re-run jobs"** → **"Re-run all jobs"**

### Option B: Nouveau Push

```bash
cd "C:\Users\Olivier Lafontaine\Desktop\Projet Minecraft\forge-1.20.1-47.3.39-mdk"

# Commit vide pour re-déclencher
git commit --allow-empty -m "chore: Re-trigger GitHub Actions"
git push
```

---

## ✅ Résultat Attendu

Après avoir configuré les permissions, le build devrait:

1. ✅ Compiler le mod avec succès
2. ✅ Créer une release "latest"
3. ✅ Uploader le fichier `.jar`

Vérifiez ensuite:
- **Actions:** https://github.com/minecraftcomportementhumain/MOD1/actions (devrait être vert)
- **Releases:** https://github.com/minecraftcomportementhumain/MOD1/releases (devrait avoir "latest")

---

## 📸 Capture d'Écran des Permissions

Les paramètres devraient ressembler à ceci:

```
Workflow permissions:
  ○ Read repository contents and packages permissions
  ● Read and write permissions    ← SÉLECTIONNER CECI

  ☑ Allow GitHub Actions to create and approve pull requests    ← COCHER CECI
```

---

**Une fois configuré, le système sera automatique pour tous les futurs pushes!** 🚀

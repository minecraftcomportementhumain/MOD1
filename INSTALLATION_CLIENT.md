# 📥 Installation et Mise à Jour Automatique du Mod

Ce guide explique comment installer et mettre à jour automatiquement le mod pour les clients.

---

## 🎮 Prérequis

- **Prism Launcher** installé
- **Instance Minecraft Forge 1.20.1** créée avec le nom: `ÉtudeComportementHumain`
- Connexion internet

---

## ⚡ MÉTHODE RECOMMANDÉE: Mise à Jour Automatique au Lancement

**La meilleure solution:** Le mod se met à jour automatiquement à chaque fois que vous lancez Minecraft!

### Configuration (Une seule fois):

1. **Ouvrez Prism Launcher**
2. **Clic droit** sur votre instance → **Edit**
3. **Settings** → **Custom Commands**
4. Dans **Pre-launch command**, collez:

**Windows:**
```batch
cmd /c "curl -L -o "$INST_MC_DIR\mods\mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar"
```

**Linux/Mac:**
```bash
curl -L -o "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" https://github.com/minecraftcomportementhumain/MOD1/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar
```

5. **Cliquez OK**
6. **C'est tout!** 🎉

### Résultat:
- ✅ À chaque lancement, le mod se met à jour automatiquement
- ✅ Vous avez toujours la dernière version
- ✅ Aucune action manuelle nécessaire

📖 **Pour plus de détails, consultez:** `INSTALLATION_AUTO_UPDATE.md`

---

## 🔄 MÉTHODE ALTERNATIVE: Script Manuel

Si vous préférez contrôler manuellement les mises à jour:

---

## 📦 Installation Initiale

### Étape 1: Télécharger le script de mise à jour

1. Allez sur la page GitHub du projet: `https://github.com/VOTRE_USERNAME/VOTRE_REPO`
2. Téléchargez le script correspondant à votre système:
   - **Windows:** `update-mod.bat`
   - **Linux/Mac:** `update-mod.sh`

### Étape 2: Configurer le script

Ouvrez le script téléchargé avec un éditeur de texte et modifiez ces 3 lignes:

```batch
set GITHUB_USER=VOTRE_USERNAME_GITHUB       ← Remplacez par le nom d'utilisateur GitHub
set GITHUB_REPO=VOTRE_REPO_NAME             ← Remplacez par le nom du repository
set PRISM_INSTANCE_NAME=VotreInstanceMinecraft  ← Remplacez par le nom de votre instance Prism
```

**Exemple:**
```batch
set GITHUB_USER=UniversiteLab
set GITHUB_REPO=minecraft-mvt-mod
set PRISM_INSTANCE_NAME=Minecraft_1.20.1_Forge
```

### Étape 3: Trouver le nom de votre instance Prism

1. Ouvrez **Prism Launcher**
2. Le nom de votre instance est affiché à gauche
3. Utilisez ce nom EXACT dans le script (sensible à la casse!)

### Étape 4: Exécuter le script

**Windows:**
- Double-cliquez sur `update-mod.bat`

**Linux/Mac:**
```bash
chmod +x update-mod.sh
./update-mod.sh
```

✅ Le mod sera téléchargé automatiquement dans le bon dossier!

---

## 🔄 Mise à Jour du Mod

**Chaque fois qu'une nouvelle version est disponible**, exécutez simplement le script:

**Windows:** Double-clic sur `update-mod.bat`
**Linux/Mac:** `./update-mod.sh`

Le script:
1. ✅ Télécharge automatiquement la dernière version depuis GitHub
2. ✅ Remplace l'ancienne version
3. ✅ Vous indique que c'est prêt

---

## 🛠️ Configuration Avancée: Pre-Launch Hook

Pour automatiser encore plus, vous pouvez configurer Prism pour télécharger le mod AVANT chaque lancement:

### Windows:
1. Clic droit sur votre instance → **Edit**
2. **Settings** → **Custom commands**
3. **Pre-launch command:**
```batch
curl -L -o "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" https://github.com/VOTRE_USER/VOTRE_REPO/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar
```

### Linux/Mac:
```bash
curl -L -o "$INST_MC_DIR/mods/mysubmod-1.0-SNAPSHOT.jar" https://github.com/VOTRE_USER/VOTRE_REPO/releases/download/latest/mysubmod-1.0-SNAPSHOT.jar
```

⚠️ **Remplacez** `VOTRE_USER` et `VOTRE_REPO` par les vraies valeurs!

---

## ❓ Dépannage

### "Le dossier de mods n'existe pas!"

**Cause:** Le nom de l'instance est incorrect ou l'instance n'a pas été lancée au moins une fois.

**Solution:**
1. Vérifiez le nom exact de votre instance dans Prism Launcher
2. Lancez l'instance au moins une fois pour créer le dossier `mods/`
3. Modifiez le script avec le bon nom

### "Le téléchargement a échoué"

**Causes possibles:**
- Pas de connexion internet
- Le repository GitHub n'est pas public
- Aucune release n'a été créée

**Solution:**
1. Vérifiez votre connexion internet
2. Vérifiez que le repository GitHub est **public**
3. Vérifiez qu'une release existe: `https://github.com/VOTRE_USER/VOTRE_REPO/releases`

### Le mod ne se charge pas

**Solution:**
1. Vérifiez que vous avez **Forge 1.20.1** installé
2. Vérifiez les logs dans Prism Launcher
3. Assurez-vous qu'il n'y a qu'une seule version du mod dans `mods/`

---

## 📍 Chemins des Dossiers

**Windows:**
```
%APPDATA%\PrismLauncher\instances\VotreInstance\.minecraft\mods\
```

**Linux:**
```
~/.local/share/PrismLauncher/instances/VotreInstance/.minecraft/mods/
```

**macOS:**
```
~/Library/Application Support/PrismLauncher/instances/VotreInstance/.minecraft/mods/
```

---

## 🤖 Pour les Développeurs

Le mod est automatiquement publié sur GitHub à chaque `git push` sur la branche `main`.

Les clients téléchargent toujours la version taggée `latest`.

---

**Besoin d'aide?** Contactez l'administrateur du serveur.

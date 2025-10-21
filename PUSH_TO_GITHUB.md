# 🚀 Instructions pour Pousser vers GitHub

Le repository est configuré pour: `https://github.com/minecraftcomportementhumain/MOD1`

---

## ⚠️ Problème d'Authentification

Vous êtes actuellement connecté avec le compte `olaf1234`, mais le repository appartient à `minecraftcomportementhumain`.

---

## ✅ Solution: Utiliser un Personal Access Token (PAT)

### Étape 1: Créer un Personal Access Token

1. **Connectez-vous sur GitHub avec le compte `minecraftcomportementhumain`**
   - https://github.com/login

2. **Allez dans Settings:**
   - Cliquez sur votre avatar (en haut à droite)
   - **Settings**

3. **Developer settings:**
   - Scroll tout en bas → **Developer settings**

4. **Personal access tokens:**
   - **Tokens (classic)**
   - **Generate new token** → **Generate new token (classic)**

5. **Configurez le token:**
   - **Note:** `MOD1 Repository Access`
   - **Expiration:** `No expiration` (ou 90 days si vous préférez)
   - **Scopes à cocher:**
     - ✅ `repo` (tous les sous-scopes)
     - ✅ `workflow`

6. **Generate token**

7. **⚠️ COPIEZ LE TOKEN IMMÉDIATEMENT** (vous ne le reverrez plus!)
   - Format: `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

### Étape 2: Configurer Git avec le Token

**Option A: Avec Git Credential Manager (Recommandé)**

```bash
cd "C:\Users\Olivier Lafontaine\Desktop\Projet Minecraft\forge-1.20.1-47.3.39-mdk"

# Pousser avec authentification
git push -u origin main
```

Quand demandé:
- **Username:** `minecraftcomportementhumain`
- **Password:** `COLLEZ_VOTRE_TOKEN_ICI` (pas le mot de passe!)

**Option B: URL avec Token (Plus Simple)**

```bash
cd "C:\Users\Olivier Lafontaine\Desktop\Projet Minecraft\forge-1.20.1-47.3.39-mdk"

# Configurer le remote avec le token
git remote set-url origin https://VOTRE_TOKEN@github.com/minecraftcomportementhumain/MOD1.git

# Pousser
git push -u origin main
```

⚠️ **Remplacez `VOTRE_TOKEN` par le token commençant par `ghp_`**

**Option C: SSH (Plus Sécurisé mais Plus Complexe)**

Si vous préférez SSH, voici les étapes:

```bash
# 1. Générer une clé SSH
ssh-keygen -t ed25519 -C "email@example.com"

# 2. Copier la clé publique
cat ~/.ssh/id_ed25519.pub

# 3. Ajouter à GitHub: Settings → SSH and GPG keys → New SSH key

# 4. Changer le remote
git remote set-url origin git@github.com:minecraftcomportementhumain/MOD1.git

# 5. Pousser
git push -u origin main
```

---

## 🎯 Après le Push Réussi

1. **Vérifiez sur GitHub:**
   - https://github.com/minecraftcomportementhumain/MOD1

2. **Vérifiez la GitHub Action:**
   - https://github.com/minecraftcomportementhumain/MOD1/actions
   - Le workflow "Build and Release Mod" devrait être en cours (~2-3 min)

3. **Attendez le build:**
   - ✅ Le build devient vert
   - Une release "latest" est créée automatiquement

4. **Vérifiez la release:**
   - https://github.com/minecraftcomportementhumain/MOD1/releases
   - Le fichier `.jar` est disponible!

---

## 🔄 Pushes Futurs

Une fois configuré avec le token, les pushes futurs seront automatiques:

```bash
git add .
git commit -m "Votre message"
git push
```

---

## 📋 Commandes Rapides

```bash
# Aller dans le dossier
cd "C:\Users\Olivier Lafontaine\Desktop\Projet Minecraft\forge-1.20.1-47.3.39-mdk"

# Vérifier le statut
git status

# Ajouter les changements
git add .

# Commit
git commit -m "Description des changements"

# Push (avec authentification la première fois)
git push
```

---

## ❓ Dépannage

### "Permission denied"
→ Vérifiez que vous utilisez le bon token du compte `minecraftcomportementhumain`

### "Authentication failed"
→ Le token est invalide ou expiré. Générez-en un nouveau.

### "Could not find repository"
→ Assurez-vous que le repository `MOD1` existe sur le compte `minecraftcomportementhumain`

---

## 🔗 Liens Importants

- **Repository:** https://github.com/minecraftcomportementhumain/MOD1
- **Actions:** https://github.com/minecraftcomportementhumain/MOD1/actions
- **Releases:** https://github.com/minecraftcomportementhumain/MOD1/releases
- **Settings:** https://github.com/minecraftcomportementhumain/MOD1/settings

---

**Besoin d'aide?** Suivez les instructions étape par étape ci-dessus! 🚀

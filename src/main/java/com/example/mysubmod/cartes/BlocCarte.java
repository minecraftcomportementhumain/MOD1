package com.example.mysubmod.cartes;

/**
 * Contenu d'un bloc du plan cartésien.
 * Couches (du bas vers le haut) : élément de base (Eau/Île/Pierre) ->
 * bonbon non-visible (sous la surface) + bonbon visible (en surface).
 * Les blocs Limite ne peuvent rien contenir d'autre.
 */
public class BlocCarte {
    public TypeElementCarte type = TypeElementCarte.VIDE;
    public int elevation = 0; // -15 à +15, relatif au niveau de la mer
    public int qteBonbonVisible = 0;
    public int qteBonbonNonVisible = 0;
    public int delaiBonbonVisible = 0;     // secondes, 0 = pas de réapparition
    public int delaiBonbonNonVisible = 0;  // secondes, 0 = pas de réapparition
    public TypeBonbonCarte typeBonbonVisible = TypeBonbonCarte.STANDARD; // Standard / Bleu / Rouge (Sous-mode 2)
    public TypeBonbonCarte typeBonbonNonVisible = TypeBonbonCarte.STANDARD; // Standard / Bleu / Rouge (spécialisation Sous-mode 3)
    public int delaiApparitionInitiale = 0; // secondes après le début de partie, 0 = dès le début (bonbon visible)
    public int delaiApparitionInitialeNonVisible = 0; // idem pour le bloc bonbon non-visible
    public int finApparitionVisible = 0; // secondes après le début de partie où le bonbon visible cesse d'apparaître/réapparaître, 0 = jamais
    public int finApparitionNonVisible = 0; // idem pour le bloc bonbon non-visible
    public int expirationVisible = 0; // secondes après son apparition où le bonbon visible non collecté disparaît, 0 = jamais
    public int expirationNonVisible = 0; // idem pour le bloc bonbon non-visible (non miné)
    /** Zone manuelle du bloc : 0 = aucune, sinon 1 + index dans {@code CarteDonnees.zones}.
     *  Rempli à l'édition et au décodage (cartes à zonage manuel) ; le fichier stocke
     *  les zones en plages, pas ce champ. */
    public int zone = 0;

    public BlocCarte() {
    }

    public BlocCarte(TypeElementCarte type, int elevation) {
        this.type = type;
        this.elevation = elevation;
    }

    public BlocCarte copier() {
        BlocCarte copie = new BlocCarte();
        copie.type = this.type;
        copie.elevation = this.elevation;
        copie.qteBonbonVisible = this.qteBonbonVisible;
        copie.qteBonbonNonVisible = this.qteBonbonNonVisible;
        copie.delaiBonbonVisible = this.delaiBonbonVisible;
        copie.delaiBonbonNonVisible = this.delaiBonbonNonVisible;
        copie.typeBonbonVisible = this.typeBonbonVisible;
        copie.typeBonbonNonVisible = this.typeBonbonNonVisible;
        copie.delaiApparitionInitiale = this.delaiApparitionInitiale;
        copie.delaiApparitionInitialeNonVisible = this.delaiApparitionInitialeNonVisible;
        copie.finApparitionVisible = this.finApparitionVisible;
        copie.finApparitionNonVisible = this.finApparitionNonVisible;
        copie.expirationVisible = this.expirationVisible;
        copie.expirationNonVisible = this.expirationNonVisible;
        copie.zone = this.zone;
        return copie;
    }

    public boolean estVide() {
        return type == TypeElementCarte.VIDE && qteBonbonVisible == 0 && qteBonbonNonVisible == 0;
    }

    public boolean memeContenu(BlocCarte autre) {
        return autre != null
            && type == autre.type
            && elevation == autre.elevation
            && qteBonbonVisible == autre.qteBonbonVisible
            && qteBonbonNonVisible == autre.qteBonbonNonVisible
            && delaiBonbonVisible == autre.delaiBonbonVisible
            && delaiBonbonNonVisible == autre.delaiBonbonNonVisible
            && typeBonbonVisible == autre.typeBonbonVisible
            && typeBonbonNonVisible == autre.typeBonbonNonVisible
            && delaiApparitionInitiale == autre.delaiApparitionInitiale
            && delaiApparitionInitialeNonVisible == autre.delaiApparitionInitialeNonVisible
            && finApparitionVisible == autre.finApparitionVisible
            && finApparitionNonVisible == autre.finApparitionNonVisible
            && expirationVisible == autre.expirationVisible
            && expirationNonVisible == autre.expirationNonVisible
            && zone == autre.zone;
    }
}

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
            && delaiApparitionInitialeNonVisible == autre.delaiApparitionInitialeNonVisible;
    }
}

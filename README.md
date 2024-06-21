Koç Gamze
Loret Mathias
Merlet Thomas

https://devcloud-tinypet.ew.r.appspot.com/
normalement inutile mais : https://github.com/Ardesisme/webandcloud-tinypet

Ce qui est (très relativement) fonctionnel : 
- Création de petition
- Signature d'une pétition 

- Liste les top100 petitions triées par date.
- système de tag sur les petitions, on peut trouver les petitions par leur tag, triées par date
- On peut afficher les gens qui ont signé une pétition.

Ce qui ne fonctionne pas :
/!\ l'authentification n'est pas fonctionnelle
/!\ liste des pétitions signées par un utilisateur
/!\ le front est ... particulier, une partie des fonctionnalités sont présentes sur la première page de liens,
l'autre partie est disponible via les boutons colorés dans le menu en cliquant sur "voir des pétitions".

Les index : 
indexes:
- kind: SignaturesPetition
  properties:
  - name: petitionId
  - name: createdAt
    direction: desc
- kind: Petition
  properties:
  - name: tags
  - name: createdAt
    direction: desc
# Frontend

Frontend Angular obsługuje logowanie do panelu firmy i platformy, wymuszoną
zmianę hasła, użytkowników, role, ustawienia firmy oraz provisioning tenantów.
Token jest przechowywany w `sessionStorage`, a widoki są ładowane leniwie.
Układ jest responsywny; osobny etap dopracuje widok mobilny.

```bash
npm ci
npm start
```

Build produkcyjny:

```bash
npm run build
```

Testy:

```bash
npm test -- --watch=false
```

# NepalGo v2 — full project scaffold

Folder tree for the whole build, per master spec v15. Only Phase 1's infra
files have real content right now; everything else is an empty folder with a
`.gitkeep` so it survives the zip and is ready to receive its Spring
Initializr download or `flutter create` / `npm create vite` output when that
phase starts.

```
nepalgo-v1\
  docker-compose.yml   .env   .gitignore   README-SETUP.md
  secrets\                        (Firebase key, JWT PEMs — gitignored, empty for now)
  infra\postgres\init\            (schema/role SQL — filled)

  eureka-server\                  Phase 1 — Spring Initializr output goes here
  api-gateway\                    Phase 1 — Spring Initializr output goes here
  auth-service\                   Phase 1 — Spring Initializr output goes here
  catalog-service\                Phase 2 — Spring Initializr output goes here
  booking-service\                Phase 3 — Spring Initializr output goes here
  payment-service\                Phase 4 — Spring Initializr output goes here
  legal-service\                  Phase 5 — Spring Initializr output goes here
  notification-service\           Phase 8 — Spring Initializr output goes here
  support-service\                Phase 8 — Spring Initializr output goes here
  mobile-app\                     Phase 6 — `flutter create .` goes here
  admin-frontend\                 Phase 7 — `npm create vite@latest .` goes here
```

## How to use this
1. Unzip into `C:\Users\kobic\IdeaProjects\nepalgo-v1\`, merging with what's
   already there.
2. Run `dir /a` and confirm `.env`, `.gitignore` and the `.sql` file kept
   their real extensions — none became `.txt`.
3. When a phase starts, generate that service/app from its tool and unzip or
   extract directly into the matching folder above, so e.g.
   `catalog-service\pom.xml` sits right at that level, not nested deeper.
4. `docker-compose.yml` has every future service listed in a comment block,
   each tagged with the phase that uncomments it — nothing else to add there
   yourself.
5. For Phase 1: `docker compose up -d`, then work through the verification
   checklist from the last message.

I built and checked every file's content in my own sandbox, but that sandbox
has no Docker daemon and no route to Docker Hub — `docker compose up` has to
run on your machine.

# Localization architecture
Focused Mind stores UI/content language separately from the device locale override. `system` follows the current Android locale; a manual choice is persisted. Content packs are offline JSON assets per locale and per domain (reminders, academic, progress). This keeps notification content deterministic and prevents random-language notifications.

The pack architecture is designed to hold the large phrase catalog without network access. Before production, phrase packs should be linguistically reviewed by native speakers, especially Cantonese, Arabic and regional Portuguese variants.

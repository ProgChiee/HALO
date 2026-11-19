# Synthetic Excel regression fixtures

These workbooks contain synthetic identities only. They were saved by installed Microsoft Excel through a separate hidden instance on 2026-11-15. Personal-information removal was enabled. No existing user workbook was opened.

- `excel-formatted.xlsx`: one student, exact four headers, shared strings, native Excel styles/metadata, borders over A1:F1005. It has 1,005 physical XML rows but only one nonempty student row. Before the fix it failed with INVALID_REGISTRY_WORKBOOK.
- `halo-template-excel-filled.xlsx`: generated directly by StudentRegistryWorkbook.template(), opened in Excel with macros disabled, guidance row replaced by one synthetic student, formatting applied over A1:F4, saved as XLSX.

Keep these as actual binary fixtures so tests exercise a real Excel save, not only a handcrafted XML generator. The compatibility test also writes and rereads the unmodified generated HALO template as an actual .xlsx file. Preview integration tests verify HTTP 200, one eligible row and no database/audit writes. Authorization remains covered separately by real-JWT embedded-Tomcat tests.

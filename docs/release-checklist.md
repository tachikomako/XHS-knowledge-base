# Release checklist

Use this checklist for the first installable MVP build.

## Automated checks

- Run `.\scripts\verify.ps1`.
- Run `.\scripts\package-extension.ps1`.
- Confirm the zip exists under `dist/`.
- Confirm the zip does not contain `node_modules`, tests, fixtures, or package files.

## Local services

- Start backend: `cd backend; .\mvnw.cmd spring-boot:run`.
- Start frontend: `cd frontend; npm run dev`.
- Open `http://127.0.0.1:5173`.
- In the website settings dialog, confirm Qwen status and AI switch.

## Chrome extension

- Open `chrome://extensions`.
- Enable developer mode.
- Load unpacked from the `extension` directory for development testing.
- For installable package testing, use the zip created in `dist/`.
- In the popup connection settings, use backend URL `http://127.0.0.1:8080` and the same token as `XHS_EXTENSION_TOKEN`.

## Real Xiaohongshu path

- Clip one open post detail page.
- Open the profile page, optionally enable favorite content completion in the popup, then click "start sync".
- Confirm the extension navigates only to `tab=fav&subTab=note` after that click.
- Sync the same post twice and confirm only one item exists.
- Confirm newly discovered list items move to completed text when their detail page can be opened, and that failed detail extraction is counted without stopping the sync.
- Open the saved item and confirm "view original post" keeps the complete `xsec_token` URL behavior.
- Confirm the website shows no imported visual media and makes no proxied media requests.
- Delete a saved item and confirm it disappears from the database; sync it again and confirm it is recreated.
- Use "清空知识库", type the exact confirmation text, and confirm items/source relations/AI suggestions are physically removed while categories, tags, settings, and latest sync history remain.
- Reopen the popup or website settings dialog and confirm the latest sync result is visible.
- With AI disabled, import saves raw content only.
- With AI enabled and Qwen configured, import creates background summary/category/tag metadata.
- In website settings, click the Qwen test button and confirm it reports success or a safe generic failure without showing the API key.
- Click "organize pending content" and confirm only completed-text, unlocked, pending/failed items are processed.
- Open one saved item and click "reorganize with AI"; confirm the updated summary/category/tag state is visible after the request.
- Open a detail item containing Xiaohongshu hashtags and confirm the original tags are shown separately from editable tags.
- In category management, generate AI category suggestions, edit at least one suggested name, confirm, and verify the created categories appear in the sidebar.
- Click the sidebar "pending" entry and confirm it shows uncategorized or failed/low-confidence AI items rather than the whole library.
- If Qwen fails, confirm the item remains and `aiStatus` becomes `FAILED`.
- After manually editing summary/category/tags, confirm later imports do not overwrite that metadata.

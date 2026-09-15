# USSD

A lightweight USSD application framework for mobile-first services in emerging markets.

## Features

- Session-based USSD menu flows
- Multi-language support
- Backend integrations (API hooks)
- Logging and analytics
- Extensible plugin architecture

## Getting Started

```bash
pip install -r requirements.txt
python -m ussd.app
```

## Project Structure

```
ussd/
  ├── app.py          # Main USSD gateway handler
  ├── menus/          # Menu definitions
  ├── handlers/       # Business logic handlers
  ├── sessions.py     # Session management
  └── config.py       # Configuration
```

## License

MIT

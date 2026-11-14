"""Makes the real companion service (services/companion) importable from this
package's tests, so pairing/auth are verified against the actual companion
code, not a reimplementation of it."""
from __future__ import annotations

import sys
from pathlib import Path

_COMPANION_SRC = Path(__file__).resolve().parents[4] / "services" / "companion"
if str(_COMPANION_SRC) not in sys.path:
    sys.path.insert(0, str(_COMPANION_SRC))

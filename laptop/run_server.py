"""Entrypoint to launch the NaviSense Laptop REST API server."""

import argparse
import sys
from pathlib import Path

# Ensure repository root is on sys.path
REPO_ROOT = Path(__file__).resolve().parent.parent
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

import uvicorn
from laptop.config.settings import LaptopConfig


def main():
    parser = argparse.ArgumentParser(description="NaviSense Laptop Locate API Server")
    parser.add_argument("--host", type=str, default="0.0.0.0", help="Host interface to bind to (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8000, help="Port to listen on (default: 8000)")
    parser.add_argument("--reload", action="store_true", help="Enable auto-reload for development")
    args = parser.parse_args()

    print("=" * 70)
    print("NaviSense Stationary Locate Service (FastAPI / Uvicorn)")
    print("=" * 70)
    print(f"Listening on : http://{args.host}:{args.port}")
    print(f"Health check : http://{args.host}:{args.port}/api/v1/health")
    print(f"Locate query : http://{args.host}:{args.port}/api/v1/objects/locate?name=keys")
    print("=" * 70)

    uvicorn.run("laptop.api.server:app", host=args.host, port=args.port, reload=args.reload)


if __name__ == "__main__":
    main()

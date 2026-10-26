#!/bin/bash
cd "$(dirname "$0")"

echo "======================================================================="
echo "  Accessible Route Adviser - RVCE Campus Navigation"
echo "  Department of CSE-AIML, RV College of Engineering"
echo "======================================================================="
echo ""

# 1. Verify Java is installed
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java was not found in your system PATH."
    echo "Please install Java 21 or later from: https://adoptium.net"
    exit 1
fi

# 2. Determine Maven command
if command -v mvn &> /dev/null; then
    echo "[OK] Using system Maven to launch application..."
    mvn javafx:run
elif [ -f "./mvnw" ]; then
    echo "[OK] Using bundled Maven Wrapper (mvnw) to launch application..."
    chmod +x ./mvnw
    ./mvnw javafx:run
else
    echo "[ERROR] Neither Maven nor mvnw wrapper was found in this folder."
    exit 1
fi

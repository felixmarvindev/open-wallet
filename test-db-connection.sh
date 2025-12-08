#!/bin/bash

# Test PostgreSQL connection from localhost
# This simulates what Spring Boot application will do

echo "Testing PostgreSQL connection..."
echo "Host: localhost"
echo "Port: 5432"
echo "Database: openwallet"
echo "Username: openwallet"
echo "Password: openwallet"
echo ""

# Test 1: Using psql (if installed locally)
if command -v psql &> /dev/null; then
    echo "Test 1: Using psql..."
    PGPASSWORD=openwallet psql -h localhost -p 5432 -U openwallet -d openwallet -c "SELECT 'Connection successful!' as status, current_user, current_database();" 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ psql connection test PASSED"
    else
        echo "❌ psql connection test FAILED"
    fi
    echo ""
else
    echo "⚠️  psql not found locally. Skipping psql test."
    echo ""
fi

# Test 2: Using Docker exec (from inside container)
echo "Test 2: Using Docker exec (from inside container)..."
docker-compose exec -T postgres psql -U openwallet -d openwallet -c "SELECT 'Connection successful!' as status, current_user, current_database();" 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Docker exec connection test PASSED"
else
    echo "❌ Docker exec connection test FAILED"
fi
echo ""

# Test 3: Using telnet/nc to check port accessibility
echo "Test 3: Checking if port 5432 is accessible..."
if command -v nc &> /dev/null; then
    nc -zv localhost 5432 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ Port 5432 is accessible"
    else
        echo "❌ Port 5432 is NOT accessible"
    fi
elif command -v telnet &> /dev/null; then
    echo "Testing with telnet..."
    (echo "quit"; sleep 1) | telnet localhost 5432 2>&1 | grep -q "Connected" && echo "✅ Port 5432 is accessible" || echo "❌ Port 5432 is NOT accessible"
else
    echo "⚠️  nc or telnet not found. Skipping port test."
fi
echo ""

# Test 4: Show Docker Compose configuration
echo "Test 4: Docker Compose PostgreSQL configuration:"
docker-compose config | grep -A 5 "postgres:" | grep -E "POSTGRES_|ports:" | head -5
echo ""

echo "Done!"


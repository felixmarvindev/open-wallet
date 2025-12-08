#!/bin/bash

# Test PostgreSQL connection on port 5433 (Docker) vs 5432 (local)

echo "Testing PostgreSQL connections..."
echo ""

echo "Test 1: Docker PostgreSQL on port 5433..."
PGPASSWORD=openwallet psql -h localhost -p 5433 -U openwallet -d openwallet -c "SELECT 'Docker connection successful!' as status, current_user, current_database();" 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Docker PostgreSQL (port 5433) connection test PASSED"
else
    echo "❌ Docker PostgreSQL (port 5433) connection test FAILED"
fi
echo ""

echo "Test 2: Local PostgreSQL on port 5432 (if exists)..."
PGPASSWORD=openwallet psql -h localhost -p 5432 -U openwallet -d postgres -c "SELECT 'Local connection test' as status;" 2>&1 | head -3
echo ""

echo "Done!"


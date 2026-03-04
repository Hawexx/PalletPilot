import postgres from 'postgres';

const sql = postgres('postgresql://postgres:Kuzey2578@localhost:5432/postgres');

async function main() {
    console.log('Attempting to create database "palletpilot"...');
    try {
        await sql`CREATE DATABASE palletpilot`;
        console.log('✅ Database "palletpilot" created successfully!');
    } catch (e) {
        // 42P04 is the error code for "database already exists"
        if (e.code === '42P04') {
            console.log('ℹ️  Database "palletpilot" already exists.');
        } else {
            console.error('❌ Error creating database:', e);
        }
    } finally {
        await sql.end();
    }
}

main();

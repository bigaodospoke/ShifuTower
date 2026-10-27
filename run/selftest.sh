#!/bin/bash
# Espera o servidor de desenvolvimento subir e manda comandos de teste pelo console.
# Uso: bash run/selftest.sh run/test-output.log | ./gradlew runServer
LOG="$1"
for i in $(seq 1 900); do
  if grep -q 'Done (' "$LOG" 2>/dev/null; then break; fi
  sleep 1
done
sleep 5
send() { echo "$1"; sleep "${2:-4}"; }
send "execute in shifutower:torre run forceload add 255984 -16 256015 15" 6
send "shifutower validar"
send "shifutower testar 1"
send "shifutower testar 10"
send "shifutower arena 10" 8
send "execute in shifutower:torre if block 256000 244 0 minecraft:white_concrete"
send "execute in shifutower:torre if block 256000 244 -2 minecraft:red_concrete"
send "execute in shifutower:torre if block 256000 244 2 minecraft:white_concrete"
send "execute in shifutower:torre if block 256000 244 0 minecraft:white_concrete"
send "execute in shifutower:torre if block 256000 245 11 minecraft:barrier"
send "execute in shifutower:torre if block 256008 245 3 minecraft:crying_obsidian"
send "execute in shifutower:torre if block 256000 247 -9 minecraft:beacon"
send "execute in shifutower:torre positioned 256000 245 -6 run data get entity @e[type=cobblemon:npc,limit=1,sort=nearest,distance=..3] CustomName"
send "execute in shifutower:torre positioned 256000 245 -6 run data get entity @e[type=cobblemon:npc,limit=1,sort=nearest,distance=..3] Tags"
send "shifutower arena 1" 8
send "execute in shifutower:torre positioned 256000 245 -6 if entity @e[type=cobblemon:npc,distance=..3]"
send "execute in shifutower:torre positioned 256000 65 -6 run data get entity @e[type=cobblemon:npc,limit=1,sort=nearest,distance=..3] CustomName"
send "execute in shifutower:torre if block 256000 64 6 minecraft:stone_bricks"
send "execute in shifutower:torre if block 256008 65 3 minecraft:stone_brick_wall"
send "execute in shifutower:torre if block 256008 68 3 minecraft:lantern"
send "stop" 1

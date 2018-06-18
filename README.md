# whirlpool-server
Whirlpool server

## Usage
- create local server configuration to override default settings:
```
cd whirlpool-server
cp src/main/resources/application.properties ./custom.properties
```

- run from commandline:
```
mvn clean install -Dmaven.test.skip=true
java -jar target/whirlpool-server-0.0.1-SNAPSHOT.jar --spring.config.location=./custom.properties [--debug]
```



## Configuration
### RPC client
```
server.rpc-client.host = CONFIGURE-ME
server.rpc-client.user = CONFIGURE-ME
server.rpc-client.password = CONFIGURE-ME
```
The bitcoin node should be running on the same network (main or test).<br/>
The node will be used to verify UTXO and broadcast tx.

### UTXO amounts
```
server.round.denomination: amount in satoshis
server.round.miner-fee: miner fee (only paid by mustMix)
```
UTXO for mustMix should be founded with *server.round.denomination*<br/>
UTXO for liquidities should be founded with *server.round.denomination*+*server.round.miner-fee*

### Round limits
```
server.round.anonymity-set-target = 10
server.round.anonymity-set-min = 6
server.round.anonymity-set-max = 20
server.round.anonymity-set-adjust-timeout

server.round.must-mix-min
server.round.liquidity-timeout
```
Round will start when *server.round.anonymity-set-target* (mustMix + liquidities) are registered.<br/>
If this target is not met after *server.round.anonymity-set-adjust-timeout*, it will be gradually decreased to *server.round.anonymity-set-min*.<br/>

At the beginning of the round, only mustMix can register and liquidities are placed on a waiting pool.<br/>
After *server.round.liquidity-timeout*, liquidities are added as soon as *server.round.must-mix-min* is reached, up to *server.round.anonymity-set-max* inputs (mustMix + liquidities).

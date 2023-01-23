TODO: Write a good README file :)

## Refactored stuff

### From AnalyioloTest.java
# CLI call options
* Create a new database and add some transactions
  $ portfolio --new-database db-name(.db) --ingest file-with-transactions

* Add some transactions into the database
  $ portfolio demo.db --ingest file-with-additional-transactions

* Compute the current value of the portfolio
  $ portfolio demo.db
  $ portfolio demo.db value

* Compute the value of the portfolio on a certain date
  $ portfolio demo.db value 2021-12-31

* Compute the TWRR between since inception, 1 year, YTD, between two dates
  $ portfolio demo.db twrr inception
  $ portfolio demo.db twrr one-year
  $ portfolio demo.db twrr ytd
  $ portfolio demo.db twrr 2021-01-01 2021-10-31

* Compute the weighted avg acquisition price for each stock held: currently, at a specific date
  $ portfolio demo.db avgCost
  $ portfolio demo.db avgCost 2021-10-10
  $ portfolio demo.db --filter=TSLA avgCost 2021-10-10

* Filter the used transactions to a specific stock
  $ portfolio demo.db --filter=TSLA
  $ portfolio demo.db --filter=TSLA,AVUV

* Get price of a specific stock: current, specific date
  $ portfolio --get-price TSLA
  $ portfolio --get-price TSLA 2021-10-10

If no date is given/what is the value of the portfolio now?
- ask Yahoo Finance the prices of the list

If the user asks for the value of the portfolio at the end of a certain date
- ~~figure out what stocks were held at the end of that day~~
- ~~ask Yahoo Finance the price for each stock~~
- ~~sum up and multiply according to the number of shares for each stock~~

Metrics to print
- ~~Current Net value~~
- ~~Net value at a specific date~~
- ~~Time weighted return, since inception, year, YTD~~
- ~~Per Stock: Current price | avg/mean buying price~~

# Internal considerations
- When combining transactions from the database and command input (directly or from a file):
    - you must sort the transactions according to date
    - How do you sort transactions that were done on the same date regarding the same stock?

## Assumptions
- Transactions in the db are already correctly sorted

* Finished tasks
    - ~~Input all the transactions into db~~
    - ~~Write methods to write multiple rows into db~~
    - ~~Write methods to read multiple rows into db~~
    - ~~compile list of all stocks held~~
    - ~~After parsing all transactions, make sure that no nShares is negative for any stock~~
    - ~~compile list of all stock held at this current moment~~
    - ~~Connect to H2 database using JDBC, write & read stuff to it~~
    - ~~Stream.java -> flattenResult~~
    - ~~check whether txType and price (pos or neg) corresponds~~
    - ~~Read all the transactions from the db into memory~~
    - ~~When parsing from file, make sure to return failure of createTxWithType()~~
    - ~~List.unfold() where you preserve the Return.failure()~~
    - ~~Write multiple transactions into db in FP style~~
    - ~~Close statements, preparedStatements and database~~
    - ~~Refactor code that gets data out of DataBase, eliminate use of statements and ResultSets
      from MainTest.java~~
    - ~~Can I remove preparedStatements from DataBase.java?~~
    - ~~Refactor into DataSource.java~~
    - ~~Refactor into Parser.java~~
    - ~~Refactor tests into DataSourceTest.java, ParserTest.java and DataBaseTest.java~~
    - ~~Parse data from file, check whether it is valid, if so, put into db~~
    - ~~Sort the newly provided transactions, if some were done on the same day, sort them according to
      the sequence they were provided~~
    - ~~Check whether the new provided transactions are all later (or on the same day) as the latest from
      the db~~
    - ~~Figure out, whether the provided data is sorted in descending or ascending order~~
    - ~~After reading in data, make sure it is sorted correctly -> if note let the user know~~
    - ~~Check for negative stocks after combining the transactions in the db and the supplied file~~
    - ~~Make sure that after each transactions there is no negative number of shares for each stock~~
    - ~~after having checked the new transactions, input them into the db~~
    - ~~Compute weighted average of stock purchase for each stock
      -> ((nShares * price) for each tx)/totShares in portfolio~~
    - ~~Create function to get value of 1 and multiple stocks, now and at specific date~~
    - ~~Time-Weighted Rate of Return (TWRR)~~
    - ~~Fix wrong dates in test data 2022 -> 2021~~
    - ~~Refactor into Portfolio class~~
      */

### From Analysiolo.java
# CLI call options
* Example: Create a new database and add some transactions
  $ analysiolo --create-database db-name(.db) --ingest file-with-transactions

* Syntactic sugar to compute the current value of the portfolio
  $ analysiolo demo.db

* Flags
  ** Without/independent of subcommand
  --dry-run/-n (boolean)
  --create-database/-c (arity 1)
  --ingest/--parse/--file/-f file-with-transactions (arity 1..*, split regex=' ')
  --database/-d db-name.db (arity 1)

** For subcommands
--filter=`stock_name` (arity 1..*, split regex)
--filter=`stock1_name`,`stock2_name` <- picocli:Split Regex ','
--period=`start_date` <- picocli: arity 1..2
--period=`start_date-end_date`
--period=inception
--period=one-year
--period=ytd or year-to-date
--date closing_price_date (arity 1)

* Subcommands
- price (date, period): db & tx file ignored, needs stock filter
    - no date or period -> current price of each stock
    - date -> price of each stock on date (make sure date is today or before)
    - period -> for each date (make sure date is today or before): price of each stock, add change metrics
- list (date, period): show transactions, always filter stocks
    - no date or period: all transactions
    - date -> every transactions up to & incl. date
    - period -> transactions between two dates (inclusive)
- value (date, period): 1) filtered transactions, always filter stocks 2) value on specific date
    - no date or period -> all transactions, value today
    - date -> transactions up to & incl. date, value on date
    - period -> compute for each date: transactions up to & incl. date, value on date
- avgCost (date, period): avgCost for each stock over filtered transactions, always filter stocks
    - no date or period -> all transactions
    - date -> filter transactions up to & incl. date
    - period -> transactions between two dates (inclusive)
- twrr (date, period): 1) filtered transactions, always filter stocks 2) twrr until specific date
    - no date or period -> all transactions
    - date -> transactions up to & incl. date, twrr on date
    - period -> transactions inside period (inclusive), twrr on second/last date

* Specifying time periods
* -> if only one date: taken as from-date with end-date today
  Following equivalent
  $ analysiolo <subcommand> -d demo.db (equiv)
  $ analysiolo <subcommand> -d demo.db --period inception (equiv)
  $ analysiolo <subcommand> -d demo.db --period inception now (equiv)
  $ analysiolo <subcommand> -d demo.db --period 2021-10-10 (from-date until now)
  $ analysiolo <subcommand> -d demo.db --period 2021-10-10 now (equiv to ^)
  $ analysiolo <subcommand> -d demo.db --period inception 2021-10-10 (from- to end-date)

* Filter the used transactions to a specific stock
  $ analysiolo <subcommand> -d demo.db --filter=TSLA,AVUV (1+ stock tickers)

* Get price of a specific stock: current, specific date
  $ analysiolo price --filter=TSLA
  $ analysiolo price --filter=TSLA --date 2021-10-10
  $ analysiolo price --filter=TSLA --period 2021-10-10 (from_date until now, everything supported except 'inception')
  $ analysiolo price --filter=TSLA --period inception (not supported)
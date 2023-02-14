# Analysiolo
Command line utility to compute metrics about a stock or a stock portfolio, including
- the current price and historical closing-price of a stock,
- the value of a portfolio,
- a stock's average, min, and max purchase price; and
- the Time-Weighted Rate or Return (TWRR) of a portfolio ([Investopedia](https://www.investopedia.com/terms/t/time-weightedror.asp), [Wikipedia](https://en.wikipedia.org/wiki/Time-weighted_return)).

# Table of Content
- [Additional Details](https://github.com/ssppooff/Analysiolo#additional-details)
	- [Implementation Details](https://github.com/ssppooff/Analysiolo#implementation-details)
- [Demo Data](https://github.com/ssppooff/Analysiolo#demo-data)
- [Roadmap / Trello](https://github.com/ssppooff/Analysiolo#roadmap--trello)
- [Usage](https://github.com/ssppooff/Analysiolo#usage)
	- [Creating a new database](https://github.com/ssppooff/Analysiolo#creating-a-new-database)
	- [Ingesting Transactions](https://github.com/ssppooff/Analysiolo#ingesting-transactions)
	- [Using an existing database](https://github.com/ssppooff/Analysiolo#using-an-existing-database)
	- [Transaction Syntax](https://github.com/ssppooff/Analysiolo#transaction-syntax)
	- [Dry-run](https://github.com/ssppooff/Analysiolo#dry-run)
- [Fetching Stock Prices](https://github.com/ssppooff/Analysiolo#fetching-stock-prices)
	- [Filtering for ticker symbols & dates](https://github.com/ssppooff/Analysiolo#filtering-for-ticker-symbols--dates)
- [Listing Transactions](https://github.com/ssppooff/Analysiolo#listing-transactions)
	- [Filtering transactions](https://github.com/ssppooff/Analysiolo#filtering-transactions)
- [Value of a Portfolio](https://github.com/ssppooff/Analysiolo#filtering-transactions)
	- [Filtering a portfolio](https://github.com/ssppooff/Analysiolo#filtering-transactions)
- [Average Cost](https://github.com/ssppooff/Analysiolo#average-cost)
	- [Filtering for average cost](https://github.com/ssppooff/Analysiolo#filtering-for-average-cost)
- [Time-Weighted Rate of Return (TWRR)](https://github.com/ssppooff/Analysiolo#time-weighted-rate-of-return-twrr)
	- [Filtering for TWRR](https://github.com/ssppooff/Analysiolo#time-weighted-rate-of-return-twrr)

# Additional Details
- Stock Market data from [Yahoo Finance](https://finance.yahoo.com)
- Transactions are input via file and stored inside a database, allowing the piecemeal build-up of the portfolio
- The stored transactions can be filtered by stocks and time periods

## Implementation Details
- Implemented in Java on SDK 17 (Amazon Coretto)
- Based on 3 modules: wrapper around YahooFinanceAPI, functional-programming library (see below), app (utilities and CLI code)
- Unit tests for main functionality in each module
- BigDecimal for currency calculations
- Using package YahooFinanceAPI ([website](https://financequotes-api.com), [github](https://github.com/sstrickx/yahoofinance-api), [javadoc](https://financequotes-api.com/javadoc/yahoofinance/YahooFinance.html))
- Utilizing own library to write in a more functional-programming style, beyond what Java natively supports (style and library based on book [_Functional Programming in Java_ by Pierre-Yves Saumont, Manning](https://www.manning.com/books/functional-programming-in-java))
- Wrapper classes around YahooFinanceAPI to use it in a functional-programming style
- Assembled into a single binary

# Demo Data
The file `demo_transactions.txt` inside the folder `demoData` contains a list of transactions for demonstration purposes. The database `demoDB.mv.db` contains the same transactions in database form. To create the same database see [Creating a new database](https://github.com/ssppooff/Analysiolo#creating-a-new-database) on how to load the transactions into a database.

# Roadmap / Trello
Trello board: https://trello.com/b/snjbAtZ6/analysiolo

# Usage
The program has several subcommands (much like Git's `git status`, `git commit`, etc.) and will output an error with a help message if run without one.

Every command needs some transactions to work (except for the subcommand `price`, see [Fetching Stock Prices](https://github.com/ssppooff/Analysiolo#fetching-stock-prices)).

The transactions are specified in a file (see [Transaction Syntax](https://github.com/ssppooff/Analysiolo#transaction-syntax)) then fed into a database. The database can be reused each time, allowing to build up the porfolio over time by inputting only the latest transactions.

### Creating a new database
To create a new database, specify a file containing transactions using `--file` or `-f` and the flag `--create-database` or `-c`.
The following code will create a new database called `myPortoflio.mv.db`, ingest the transactions in `newTransactions.txt` into it, and then list all the transactions inside the database.
```
analysiolo list -c ~/home/myPortfolio -f ~/home/newTransactions.txt
```

### Ingesting Transactions
Every time a subcommand is run, you can ingest new transactions by specifying which file to use with `--file` or `-f`:
```
analysiolo value -d ~/home/myPortfolio -f ~/home/newTransactions.txt
```

### Using an existing database
Specify which database-file to use using the flag `--database` or `-d`, e.g.
```
analysiolo list -d ~/home/myPortfolio.mv.db
```

It is also possible to ingest a file containing new transactions at the same time using `--file` or `-f` (see [Ingesting Transactions](https://github.com/ssppooff/Analysiolo#ingesting-transactions)).
The following code will first read the transactions in `newTransactions.txt`, ingest them into the database `myPortfolio.mv.db`, and then list the combined set of transactions.
```
analysiolo list -d ~/home/myPortfolio.mv.db -f ~/home/newTransactions.txt
```

### Transaction Syntax
In a plaintext file (extension doesn't matter), each line represents a transaction formatted as follows
- date in reverse (e.g. `2020-12-30`)
- Whether it was a `BUY` or a `SELL`
- The ticker symbol
- The number of shares bought (positive number) or sold (negative number)
- The price (always positive)

For example, if on February 18, 2021, you bought 11 shares of S&P 500 (ticker `^GSPC`) for 3'930.60, it should be written as
```
2021-02-18 BUY ^GSPC +11 3930.60
```

Or, selling 5 shares of S&P 500 on March 3, 2021, for 3'820.40, should be written as
```
2021-03-03 SELL ^GSPC -5 3820.40
```

### Dry-run
With the flag `--dry-run` or `-n` you can try out a subcommand without `analysiolo` writing or opening a file.
This can be used to double check all arguments:
```
$ analysiolo value -n -d ~/home/myPortfolio.mv.db -f ~/home/newTransactions.txt --period 2022-01-10 2022-07-10
Using existing database at ~/home/myPortfolio.mv.db
Ingesting transactions into database from file ~/home/newTransactions.txt
Computing value of portfolio on date 2022-01-10
Computing value of portfolio on date 2022-07-10
Adding change metrics
Outputting result
```

## Fetching Stock Prices
The subcommand `price` directly fetches price information from [Yahoo Finance](https://finance.yahoo.com), i.e., no transactions are necessary. However, at least one Yahoo ticker symbol must be given, and note that the ticker symbol is not always very intuitive (e.g. `^GSPC` for the S&P 500). Also, depending on your shell some symbols may need double quotes (e.g., for zsh `"^GSPC"`).

Additionally, a date or time period can be given using `--date` or `--period`, respectively.

### Filtering for ticker symbols & dates
Some examples to better illustrate what will be output with which flag:

- No date or period -> current market price for each stock (time of writing 2023-01-31 12:52)
  ```
  $ analysiolo price --filter ^GSPC
         2023-01-31
   ----- ----------
   ^GSPC   4017.770
  ```

- Single date with `--date` -> closing price of each ticker symbol on the given date
  ```
  $ analysiolo price --filter ^GSPC TSLA --date 2021-01-02
         2021-01-02
   ----- ----------
   ^GSPC   3735.360
   TSLA     221.230
  ```

- Time period -> closing price at the beginning and end of the period, as well as the price changes (absolute and in percentage)
  ```
  $ analysiolo price --filter ^GSPC TSLA --period 2021-01-02 2022-01-02
         2021-01-02 ~> 2022-01-02 |       𝝙 𝝙 (%)
   ----- ----------    ---------- + -------- ------
   ^GSPC   3735.360 ~>   4786.350 | 1050.990 28.136
   TSLA     221.230 ~>    362.823 |  141.593 64.003
  ```

## Listing Transactions
The transactions inside the database can be displayed with the subcommand `list`, see [Using an existing database](https://github.com/ssppooff/Analysiolo#using-an-existing-database).
```
analysiolo list -d ~/home/myPortfolio.mv.db
```
It can also be combined with ingesting new transactions (see [Ingesting Transactions](https://github.com/ssppooff/Analysiolo#ingesting-transactions)):
```
analysiolo list -d ~/home/myPortfolio.mv.db -f ~/home/newTransactions.txt
```

### Filtering transactions
Additionally, it is possible to limit which transactions to list (note: if a file containing transactions is given, all transactions therein are ingested into the database).

- `--filter` limits transactions to the ticker symbols given
- `--date` displayes transactions from that date only
- `--period` displayes transactions made during that time period (inclusive)
- `--date` or `--period` can be used, both can be combined with `--filter`

For example
```
analysiolo list -d ~/home/myPortfolio.mv.db --filter ^GSPC TSLA --period 2021-01-02 2021-07-02
```
will list only transactions about S&P 500 (symbol `^GSPC`) and Tesla, that were made between Jan 2, 2021, and July 2, 2021.

## Value of a Portfolio
The subcommand `value` will compute the value at a given date of a portfolio created from a set of transactions. If no date/period filter or ticker symbol filter is given, it will use all recorded transactions and compute the current value.

### Filtering a portfolio
Using the filters `--filter`, `--date`, and `--period` one can influence which transactions are being considered and for what date the value of the portfolio should be computed.

For example, the following command
```
analysiolo value -d ~/home/myPortfolio.mv.db --filter ^GSPC TSLA --date 2020-02-10
```
will …
1. use the transactions stored in `myPortfolio.mv.db`
2. filter the transactions to only the ones about S&P 500 and Tesla stocks,
3. further filter them to only the ones up until February 10, 2020,
4. compute which shares are left on the given date (Feb 10, 2020),
5. compute the value of these shares on the given date.

On the other hand
```
analysiolo value -d ~/home/myPortfolio.mv.db --filter ^GSPC TSLA --period 2020-02-10 2021-03-25
```
will compute the value on both dates, like the previous example, and then compute the difference between each value as an absolute and as a percentual change.

## Average Cost
The subcommand `avgCost` will compute the average, minimum, and maximum acquisition price paid for each stock purchased.

### Filtering for average cost
Using the filters `--filter`, `--date`, and `--period` one can influence which transactions are being considered.

To consider only the transactions up until a certain date, use `--date`. To limit the transactions to a time period, use `--period`.

For example
```
analysiolo avgCost -d ~/home/myPortfolio.mv.db --date 2020-02-10
```
will consider the transactions stored in `myPortfolio.mv.db` up until February 10, 2020. 

Whereas,
```
analysiolo avgCost -d ~/home/myPortfolio.mv.db --period 2020-02-10 2021-03-25
```
will consider only the transactions between February 10, 2020 and March 25, 2021 (inclusive).

## Time-Weighted Rate of Return (TWRR)
The subcommand `twrr` commputes the Time-Weighted Rate of Return (TWRR, see [Wikipedia](https://en.wikipedia.org/wiki/Time-weighted_return) or [Investopedia](https://www.investopedia.com/terms/t/time-weightedror.asp)).

TWRR is a method to calculate investment returns which compensates for money in- and outflows (cf. Money-Weighted Rate of Return/MWRR, [Wikipedia](https://en.wikipedia.org/wiki/Rate_of_return#Money-weighted_rate_of_return) or [Investopedia](https://www.investopedia.com/terms/m/money-weighted-return.asp)).
It is useful for comparing different portfolio strategies, irrespective of how much money is being invested and when.

### Filtering for TWRR
Differently to the other subcommands, the TWRR is always computed over a time period.
The difference is that using `--date 2020-01-01` is equivalent to telling `analysiolo` to use the period from the first transaction up to January 1, 2020. or said differently, it is equivalent to using `--period inception 2020-01-01`.

When no filter is given, e.g.
```
analysiolo twrr -d ~/home/myPortfolio.mv.db
```
then the TWRR is calculated over all transactions (`--period inception now`).

There are two ways to filter the transactions up to a certain date (say June 10, 2022),
```
# using --date
analysiolo twrr -d ~/home/myPortfolio.mv.db --date 2022-06-10              
# or --period
analysiolo twrr -d ~/home/myPortfolio.mv.db --period inception 2022-06-10
```

To instead filter the transaction starting from a certain date (say, again June 10, 2022), use `--period` with `now`
```
analysiolo twrr -d ~/home/myPortfolio.mv.db --period 2022-06-10 now
```

And to filter between two dates (June 10, 2022 and January 25, 2023), use `--period`
```
analysiolo twrr -d ~/home/myPortfolio.mv.db --period 2022-06-10 2023-01-25
```

Similarly to the other subcommands, `--filter` filters for certain stocks, e.g. to filter for S&P 500 and Tesla shares
```
analysiolo twrr -d ~/home/myPortfolio.mv.db --period 2022-06-10 2023-01-25 --filter ^GSPC TSLA
```

# Syntax for describing transactions
- TODO

# Features
## General note
Except for command price, every command accepts a database and/or file with transactions to be ingested.

## Fetch price of stock
- current price
- closing price on a specific date, or on two dates with corresponding change in price

## List transactions, filtered or not
- List transactions in database
- If a file is given, the transacations therein are included
- The transactions are filtered according to the filters given

## Value of a portfolio
- Current value of portfolio
- Current value of portion of portfolio based on filters
- Value at beginning and end of time period, with change in value

## Average, Min, Max Purchase Price
- Average, min, max purchase price of one or multiple stocks
    - Considering all transactions
    - Considering all transactions up to/incl. a specific date
    - Considering all transactions during a certain time period

# Time-Weighted Rate of Return
Link: Wikipedia
- Makes it able to compare the performance of a portfolio irrespective of money put in or taken out
- Can be computed for a portion of the portfolio
- Based on timeframe
- Based on stocks

- The available subcommands are:
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

# Filters
## Stock
## Date

Each subcommand supports some basic filters, which 
    always based on given filters and dates
    - price: lists the share prices (independent of saved transactions)
    - list: lists the saved transactions based on the given filters
    - value: returns the value of the portfolio based on the filtered transactions
    - avgCost: returns the average purchase price, as well as its minimum and maximum
    - twrr: computes the Time-Weighted Rate of Return during a period

Conceptually it can be thought of as the following:
- Base list of transactions
- Base list is filtered according to the 3 filters
- Either this list is directly processed into a Portfolio object on which the required metric is computed
- Or multiple Portfolio Objects are created (eg. for two different dates), which are then used to compute the desired metric.
- Example of for two Portfolio objects: value if a period is given instead of a single date



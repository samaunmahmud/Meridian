-- 1. cash: balance must equal the starting 10000 plus every USD ledger entry
select count(*) portfolios_checked,
       sum(abs(p.cash_balance - (10000 + coalesce(t.total,0))) > 0.0001) cash_mismatches,
       round(sum(p.cash_balance - (10000 + coalesce(t.total,0))),4) net_cash_discrepancy
from portfolio p
left join (select portfolio_id, sum(amount) total from transactions where currency is null or currency='USD' group by portfolio_id) t on t.portfolio_id = p.id
where p.user_id in (select id from users where email like 'load%');

-- 2. shares: each holding must equal filled buys minus filled sells
select count(*) positions_checked,
       sum(abs(coalesce(h.quantity,0) - o.net) > 0.00005) share_mismatches,
       round(sum(coalesce(h.quantity,0) - o.net),4) net_share_discrepancy
from (select portfolio_id, ticker_id,
             sum(case when type='BUY' then quantity else -quantity end) net
      from orders where status='FILLED' group by portfolio_id, ticker_id) o
left join holdings h on h.portfolio_id=o.portfolio_id and h.ticker_id=o.ticker_id
where o.net <> 0 or h.quantity is not null;

alter table tax_returns add column street_address clob;
alter table tax_returns add column postal_code clob;
alter table tax_returns add column city clob;
alter table tax_returns add column iban clob;
alter table tax_returns add column bank_account_holder clob;
alter table tax_returns add column tax_office clob;
alter table tax_returns add column current_interview_step varchar(32) not null default 'BASE_DATA';

alter table export_bundles add column elster_export_path clob;

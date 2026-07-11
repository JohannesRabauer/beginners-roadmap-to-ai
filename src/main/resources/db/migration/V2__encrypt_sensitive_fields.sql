alter table persons alter column first_name clob;
alter table persons alter column last_name clob;
alter table persons alter column tax_id clob;
alter table persons alter column marital_status clob;

alter table wage_tax_certificates alter column employer_name clob;
alter table wage_tax_certificates alter column gross_wages clob;
alter table wage_tax_certificates alter column wage_tax clob;
alter table wage_tax_certificates alter column solidarity_surcharge clob;
alter table wage_tax_certificates alter column church_tax clob;

alter table deduction_items alter column category clob;
alter table deduction_items alter column subcategory clob;
alter table deduction_items alter column amount clob;
alter table deduction_items alter column evidence_status clob;

alter table validation_issues alter column field_path clob;
alter table validation_issues alter column message clob;

alter table export_bundles alter column pdf_path clob;

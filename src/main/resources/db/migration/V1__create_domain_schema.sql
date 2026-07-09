create table tax_returns (
    id uuid primary key,
    tax_year integer not null,
    filing_mode varchar(16) not null,
    status varchar(16) not null,
    completed_at timestamp with time zone,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table persons (
    id uuid primary key,
    tax_return_id uuid not null,
    role varchar(16) not null,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    tax_id varchar(20),
    marital_status varchar(32),
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_persons_tax_return foreign key (tax_return_id) references tax_returns (id) on delete cascade
);

create index idx_persons_tax_return_id on persons (tax_return_id);

create table wage_tax_certificates (
    id uuid primary key,
    tax_return_id uuid not null,
    person_id uuid,
    employer_name varchar(200) not null,
    gross_wages decimal(19,2) not null,
    wage_tax decimal(19,2) not null,
    solidarity_surcharge decimal(19,2) not null,
    church_tax decimal(19,2) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_wage_tax_certificates_tax_return foreign key (tax_return_id) references tax_returns (id) on delete cascade,
    constraint fk_wage_tax_certificates_person foreign key (person_id) references persons (id) on delete set null
);

create index idx_wage_tax_certificates_tax_return_id on wage_tax_certificates (tax_return_id);
create index idx_wage_tax_certificates_person_id on wage_tax_certificates (person_id);

create table deduction_items (
    id uuid primary key,
    tax_return_id uuid not null,
    person_id uuid,
    category varchar(64) not null,
    subcategory varchar(64),
    amount decimal(19,2) not null,
    evidence_status varchar(32) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_deduction_items_tax_return foreign key (tax_return_id) references tax_returns (id) on delete cascade,
    constraint fk_deduction_items_person foreign key (person_id) references persons (id) on delete set null
);

create index idx_deduction_items_tax_return_id on deduction_items (tax_return_id);
create index idx_deduction_items_person_id on deduction_items (person_id);

create table validation_issues (
    id uuid primary key,
    tax_return_id uuid not null,
    severity varchar(16) not null,
    field_path varchar(255) not null,
    message varchar(2000) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_validation_issues_tax_return foreign key (tax_return_id) references tax_returns (id) on delete cascade
);

create index idx_validation_issues_tax_return_id on validation_issues (tax_return_id);

create table export_bundles (
    id uuid primary key,
    tax_return_id uuid not null unique,
    pdf_path varchar(500),
    elster_shape_version varchar(32) not null,
    export_status varchar(32) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_export_bundles_tax_return foreign key (tax_return_id) references tax_returns (id) on delete cascade
);

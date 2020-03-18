CREATE DATABASE wearefrank_db;
GO
USE [wearefrank_db]
GO 

CREATE TABLE CUSTOMER([CustomerID] [int] NOT NULL, [SalesAmount] [decimal] NOT NULL);
GO 

INSERT INTO CUSTOMER (CustomerID, SalesAmount) VALUES (1,100),(2,200),(3,300);
GO

CREATE LOGIN wearefrank_user WITH PASSWORD = 'wearefrankPass01';
GO

CREATE USER wearefrank_user FOR LOGIN wearefrank_user;
GO

ALTER ROLE db_owner ADD MEMBER [wearefrank_user];
GO
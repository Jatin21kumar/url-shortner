$aws = "$env:APPDATA\Python\Python314\Scripts\aws.cmd"
& "$aws" dynamodb create-table `
  --table-name url-users `
  --attribute-definitions AttributeName=username,AttributeType=S `
  --key-schema AttributeName=username,KeyType=HASH `
  --billing-mode PAY_PER_REQUEST `
  --region ap-south-1
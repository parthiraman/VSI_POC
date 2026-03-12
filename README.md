# VSI_POC

A **Proof of Concept (POC)** for IBM Integration Bus / Message Broker, demonstrating a simple message processing pipeline with Jenkins CI/CD integration.

## Overview

This project provides a minimal IBM Integration Bus message flow that reads messages from an IBM MQ input queue, passes them through an ESQL Compute module, and writes the result to an output queue. It is intended to validate build and deployment pipelines using Jenkins.

## Tech Stack

| Technology | Purpose |
|---|---|
| IBM Integration Bus (Message Broker) | Message processing runtime |
| ESQL (Enterprise Service Query Language) | Message transformation logic |
| IBM MQ | Message queuing middleware |
| Eclipse IDE | Development environment |
| Jenkins | CI/CD pipeline |

## Project Structure

```
VSI_POC/
├── TestJenkinsBuild/
│   ├── .project                  # Eclipse project descriptor
│   ├── application.descriptor    # Application configuration
│   ├── Test_Build.msgflow        # IBM Message Flow definition
│   └── Test_Build_Compute.esql   # ESQL Compute module
└── .metadata/                    # Eclipse workspace metadata
```

## Message Flow

The `Test_Build.msgflow` defines the following pipeline:

```
[MQ Input (InputQ)] --> [Compute Node] --> [MQ Output (OutputQ)]
```

1. **MQ Input Node** – Reads messages from the `InputQ` queue.
2. **Compute Node** – Processes messages using the `Test_Build_Compute` ESQL module.
3. **MQ Output Node** – Writes the processed messages to the `OutputQ` queue.

## ESQL Module

The `Test_Build_Compute.esql` module contains two procedures:

- **`CopyEntireMessage()`** – Copies the entire input message (headers + body) to the output. This is the currently active procedure.
- **`CopyMessageHeaders()`** – Copies only the message headers (available but not active by default).

```esql
CREATE COMPUTE MODULE Test_Build_Compute
  CREATE FUNCTION Main() RETURNS BOOLEAN
  BEGIN
    CALL CopyEntireMessage();
    RETURN TRUE;
  END;

  CREATE PROCEDURE CopyEntireMessage() BEGIN
    SET OutputRoot = InputRoot;
  END;
END MODULE;
```

## Getting Started

### Prerequisites

- IBM Integration Bus (v9 or later) or IBM App Connect Enterprise
- IBM MQ
- Eclipse with IBM Integration Toolkit plugin
- Jenkins (for CI/CD)

### Build & Deploy

1. **Import the project** into Eclipse using the IBM Integration Toolkit.
2. **Build the project** – Eclipse will automatically invoke the IBM Message Broker builders defined in `.project`.
3. **Deploy to a broker** – Deploy the generated BAR file to your IBM Integration Bus / ACE server.
4. **Jenkins pipeline** – Configure a Jenkins job pointing to this repository to automate build and deployment.

## CI/CD

This POC is designed to be built and deployed via Jenkins. The project structure is compatible with the `mqsicreatebar` command-line tool for generating deployable BAR files in headless build environments.

## License

This project is a proof of concept and is provided as-is for demonstration purposes.

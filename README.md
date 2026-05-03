# IPR Safety Camera AI Agent

IPR Safety Camera AI Agent is an automated, intelligent safety surveillance solution. Designed for efficiency and accuracy, this agent processes camera feeds in real-time to detect safety hazards, monitor environments, and alert personnel to potential risks. 

Our application is built for seamless deployment, ensuring that organizations can easily integrate AI-powered safety monitoring into their existing infrastructure.

---

## Quick Start for Users

Experience the power of the IPR Safety Camera AI Agent instantly. Download the standalone installer for a streamlined, one-click setup process.

<p align="center">
  <a href="https://github.com/GekiNawaii/IPR-Safety-Camera-AI-Agent/raw/main/IPR-Safety-Camera-Setup.exe">
    <img src="https://img.shields.io/badge/Download_Installer-IPR__Safety__Camera__Setup.exe-blue?style=for-the-badge&logo=windows" alt="Download Installer" />
  </a>
</p>

*Requires Windows OS. Run the downloaded executable to install the application and necessary background services.*

---

## Developer Documentation

For developers, researchers, and engineers looking to understand, build, or modify the source code, the repository is structured into specific functional modules. The core technologies driving this application include Java and Python.

### Project Structure

*   **/client**
    Contains the frontend and client-side application code. This module handles user interactions, the graphical interface, and communication with the local or remote AI server. Primarily built using Java.

*   **/server**
    The core AI and backend processing engine. This directory holds the computer vision models, video stream processing logic, and the Python-based AI agent that analyzes the safety camera feeds in real-time.

*   **/installer**
    Contains the NSIS (Nullsoft Scriptable Install System) and Batch scripts used to compile and bundle the application into a single executable (`IPR-Safety-Camera-Setup.exe`). Use these scripts to generate new release binaries after modifying the client or server code.

### Getting Started for Developers

1. Clone the repository to your local environment.
2. Review the dependencies required in the `/server` directory (Python) to set up your AI environment.
3. Compile the `/client` source code using your preferred Java build tool.
4. To build a new release, execute the scripts within the `/installer` directory.
